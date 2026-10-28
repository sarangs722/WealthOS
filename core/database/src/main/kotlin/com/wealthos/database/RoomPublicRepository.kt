package com.wealthos.database

import androidx.room.withTransaction
import com.wealthos.finance.*
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

class RoomPublicRepository(private val db:BankDatabase,private val clock:Clock=Clock.systemDefaultZone()):PublicDataRepository {
    private val dao=db.publicDao()
    override suspend fun setNetwork(enabled:Boolean):Unit=db.withTransaction {
        dao.save(PublicSettingsEntity(1,enabled,clock.instant().toString()));audit(UUID.randomUUID(),"PUBLIC_NETWORK_SETTING","Public reference refresh enabled=$enabled")
    }
    override suspend fun saveBinding(binding:NavBinding):Unit=db.withTransaction {
        val i=requireNotNull(db.portfolioDao().instruments().find {it.id==binding.instrumentId.toString()}).domain()
        require(i.category==AssetCategory.MUTUAL_FUND && i.currency==CurrencyCode.INR && i.tracking==TrackingMode.TRANSACTIONS){"AMFI binding is only for INR mutual-fund unit holdings."}
        require(Regex("[0-9]{5,9}").matches(binding.schemeCode) && (binding.isin.isEmpty() || Regex("[A-Z]{2}[A-Z0-9]{9}[0-9]").matches(binding.isin))){"Enter the exact numeric AMFI scheme code and optional 12-character ISIN."}
        val revision=(dao.bindings().filter {it.instrumentId==i.id.toString()}.maxOfOrNull {it.revision} ?: 0)+1
        dao.insert(NavBindingEntity(binding.id.toString(),i.id.toString(),binding.schemeCode,binding.isin,binding.active,revision,clock.instant().toString()))
        audit(binding.id,"AMFI_BINDING",binding.copy(revision=revision).toString())
    }
    override suspend fun cache(provider:PublicProvider,rows:List<PublicDatum>,attemptedAt:Instant,retrievedAt:Instant,message:String):Unit=db.withTransaction {
        require(rows.size in 1..500 && message.length<=400 && retrievedAt>=attemptedAt && retrievedAt<=clock.instant().plusSeconds(60))
        require(!provider.network || dao.settings()?.networkEnabled==true){"Enable optional public network refresh first."}
        require(rows.all {it.provider==provider});rows.forEach {PublicRules.validate(it,retrievedAt)}
        require(rows.map {it.series to it.date}.distinct().size==rows.size){"Duplicate/conflicting observations in one provider batch."}
        val existing=dao.observations().map {it.domain()};var count=0
        for(row in rows){val previous=existing.filter {it.datum.provider==provider && it.datum.series==row.series && it.datum.date==row.date};val hash=fingerprint(row)
            if(previous.maxByOrNull {it.revision}?.fingerprint==hash)continue
            val next=PublicObservation(UUID.randomUUID(),row,retrievedAt,(previous.maxOfOrNull {it.revision} ?: 0)+1,hash);dao.insert(next.entity());count++
        }
        val id=UUID.randomUUID();dao.insert(PublicRunEntity(id.toString(),provider.name,attemptedAt.toString(),clock.instant().toString(),true,count,message))
        audit(id,"PUBLIC_CACHE","$provider: $count new public observations; no financial posting")
    }
    override suspend fun recordFailure(provider:PublicProvider,attemptedAt:Instant,message:String):Unit=db.withTransaction {
        require(message.length in 1..400);val id=UUID.randomUUID();dao.insert(PublicRunEntity(id.toString(),provider.name,attemptedAt.toString(),clock.instant().toString(),false,0,message));audit(id,"PUBLIC_REFRESH_FAILED","$provider: cached observations retained")
    }
    override suspend fun applyNav(observationId:UUID,instrumentId:UUID,replaceSameDay:Boolean):Unit=db.withTransaction {
        val book=db.readBook();val observation=requireNotNull(book.publicData.observations.find {it.id==observationId});val d=observation.datum;PublicRules.validate(d,observation.retrievedAt)
        require(d.kind==PublicKind.NAV && d.provider==PublicProvider.AMFI_FILE)
        val binding=requireNotNull(book.publicData.currentBindings().find {it.instrumentId==instrumentId}){"Link this holding to an exact AMFI code first."}
        require(d.series=="NAV:${binding.schemeCode}" && (binding.isin.isEmpty() || binding.isin in d.identity.split('|'))){"Cached NAV identity does not match the current binding."}
        if(book.publicData.applications.any {it.observationId==observationId && it.instrumentId==instrumentId})return@withTransaction
        require(replaceSameDay || book.portfolio.valuations.none {it.instrumentId==instrumentId && it.date==d.date}){"Review and confirm the retained same-day valuation revision."}
        val value=AssetValuation(UUID.randomUUID(),instrumentId,ValuationKind.UNIT_PRICE,d.value,d.date,0,clock.instant(),"Explicitly applied AMFI file NAV; source observation $observationId. ${d.validation.label}.")
        RoomPortfolioRepository(db,clock).addValuation(value)
        val id=UUID.randomUUID();dao.insert(PublicApplicationEntity(id.toString(),observationId.toString(),"asset:$instrumentId",instrumentId.toString(),value.id.toString(),null,clock.instant().toString()));audit(id,"APPLY_PUBLIC_NAV","Source $observationId; value ${value.id}; earlier valuations retained")
    }
    override suspend fun applyFx(observationId:UUID,replaceSameDay:Boolean):Unit=db.withTransaction {
        val book=db.readBook();val observation=requireNotNull(book.publicData.observations.find {it.id==observationId});val d=observation.datum;PublicRules.validate(d,observation.retrievedAt)
        require(d.kind==PublicKind.FX && d.provider==PublicProvider.ECB && d.currency!=null && d.currency!=CurrencyCode.INR)
        if(book.publicData.applications.any {it.observationId==observationId && it.fxId!=null})return@withTransaction
        val same=book.portfolio.fx.filter {it.currency==d.currency && it.date==d.date}
        require(replaceSameDay || same.isEmpty()){"Review and confirm the retained same-day FX revision."}
        DecimalInput.positive(d.value.toPlainString());val id=UUID.randomUUID()
        db.portfolioDao().insert(FxEntity(id.toString(),d.currency!!.name,d.value.toPlainString(),d.date.toString(),(same.maxOfOrNull {it.revision} ?: 0)+1,clock.instant().toString(),"Explicitly applied ECB reference; source observation $observationId. ${d.details}"))
        val applied=UUID.randomUUID();dao.insert(PublicApplicationEntity(applied.toString(),observationId.toString(),"fx:${d.currency}",null,null,id.toString(),clock.instant().toString()));audit(id,"APPLY_PUBLIC_FX","Source $observationId; prior FX revisions retained")
    }
    private fun fingerprint(d:PublicDatum):String {val source=if(d.provider==PublicProvider.BIS)d.sourceUrl.substringBefore('?') else d.sourceUrl
        val fields=listOf(d.provider.name,d.kind.name,d.series,d.label,d.value.stripTrailingZeros().toPlainString(),d.currency?.name ?: "",d.units,d.date.toString(),source,d.validation.name,d.basis,d.details,d.publication,d.identity)
        return MessageDigest.getInstance("SHA-256").digest(fields.joinToString(""){"${it.length}:$it"}.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    }
    private suspend fun audit(id:UUID,action:String,after:String)=db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),id.toString(),action,clock.instant().toString(),null,after))
}
