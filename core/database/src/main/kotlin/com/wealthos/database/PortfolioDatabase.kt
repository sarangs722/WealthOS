package com.wealthos.database

import androidx.room.*
import com.wealthos.finance.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName="instruments",foreignKeys=[ForeignKey(entity=AccountEntity::class,parentColumns=["id"],childColumns=["custodyAccountId"],onDelete=ForeignKey.RESTRICT)],indices=[Index("custodyAccountId")])
data class InstrumentEntity(@PrimaryKey val id:String,val name:String,val category:String,val currency:String,val tracking:String,
    val openedOn:String,val createdAt:String,val liquid:Boolean,val region:String,val unit:String,val custodyAccountId:String?)

@Entity(tableName="investment_entries",foreignKeys=[ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["instrumentId","sequence"],unique=true)])
data class EntryEntity(@PrimaryKey val id:String,val instrumentId:String,val kind:String,val date:String,val sequence:Int,
    val units:String,val gross:String?,val fees:String,val ratioDenominator:String,val note:String,val recordedAt:String)

@Entity(tableName="asset_valuations",foreignKeys=[ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["instrumentId","date","revision"],unique=true)])
data class ValuationEntity(@PrimaryKey val id:String,val instrumentId:String,val kind:String,val value:String,val date:String,
    val revision:Int,val recordedAt:String,val note:String,@ColumnInfo(defaultValue="0") val includedContractSequence:Int=0,
    @ColumnInfo(defaultValue="0") val includedInvestmentSequence:Int=0,
    val reportedInvested:String?=null,val reportedUnits:String?=null,val reportedPrice:String?=null,
    @ColumnInfo(defaultValue="0") val calculatedTotal:Boolean=false)

@Entity(tableName="financial_contracts",foreignKeys=[ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)])
data class ContractEntity(@PrimaryKey val instrumentId:String,val openingPrincipal:String,val annualRate:String,val start:String,
    val end:String,val convention:String,val scheduledInstallment:String,val termMonths:Int)

@Entity(tableName="contract_events",foreignKeys=[ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["instrumentId","sequence"],unique=true)])
data class ContractEventEntity(@PrimaryKey val id:String,val instrumentId:String,val kind:String,val date:String,val sequence:Int,
    val principal:String,val interest:String,val fees:String,val recordedAt:String,val note:String)

@Entity(tableName="cash_transfers")
data class TransferEntity(@PrimaryKey val id:String,val currency:String,val amount:String,val date:String,val recordedAt:String,val note:String)

@Entity(tableName="cash_movements",foreignKeys=[
    ForeignKey(entity=AccountEntity::class,parentColumns=["id"],childColumns=["accountId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=ObservationEntity::class,parentColumns=["id"],childColumns=["anchorObservationId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=EntryEntity::class,parentColumns=["id"],childColumns=["entryId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=ContractEventEntity::class,parentColumns=["id"],childColumns=["contractEventId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=TransferEntity::class,parentColumns=["id"],childColumns=["transferId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("accountId"),Index("anchorObservationId"),Index("entryId"),Index("contractEventId"),Index("transferId")])
data class CashEntity(@PrimaryKey val id:String,val accountId:String,val currency:String,val date:String,val amount:String,
    val anchorObservationId:String,val applyToAnchor:Boolean,val entryId:String?,val contractEventId:String?,val transferId:String?,val recordedAt:String,val note:String) {
    init {require(listOf(entryId,contractEventId,transferId).count {it!=null}==1) {"A cash leg must reference exactly one financial event."}}
}

@Entity(tableName="fx_observations",indices=[Index(value=["currency","date","revision"],unique=true)])
data class FxEntity(@PrimaryKey val id:String,val currency:String,val inrPerUnit:String,val date:String,val revision:Int,val recordedAt:String,val note:String)

@Entity(tableName="statement_references",foreignKeys=[ForeignKey(entity=AccountEntity::class,parentColumns=["id"],childColumns=["accountId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["accountId","date","revision"],unique=true)])
data class ReferenceEntity(@PrimaryKey val id:String,val accountId:String,val total:String,val date:String,val revision:Int,val recordedAt:String)

@Dao interface PortfolioDao {
    @Query("SELECT * FROM cash_transfers ORDER BY id") suspend fun transfers():List<TransferEntity>
    @Query("SELECT * FROM instruments ORDER BY name COLLATE NOCASE, id") suspend fun instruments():List<InstrumentEntity>
    @Query("SELECT * FROM investment_entries ORDER BY instrumentId, date, sequence") suspend fun entries():List<EntryEntity>
    @Query("SELECT * FROM asset_valuations") suspend fun valuations():List<ValuationEntity>
    @Query("SELECT * FROM financial_contracts") suspend fun contracts():List<ContractEntity>
    @Query("SELECT * FROM contract_events ORDER BY instrumentId, date, sequence") suspend fun events():List<ContractEventEntity>
    @Query("SELECT * FROM cash_movements") suspend fun cash():List<CashEntity>
    @Query("SELECT * FROM fx_observations") suspend fun fx():List<FxEntity>
    @Query("SELECT * FROM statement_references") suspend fun references():List<ReferenceEntity>
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:InstrumentEntity)
    @Update suspend fun update(value:InstrumentEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:EntryEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ValuationEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ContractEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ContractEventEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:CashEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:TransferEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:FxEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:ReferenceEntity)
}

internal fun InstrumentEntity.domain()=Instrument(UUID.fromString(id),name,AssetCategory.valueOf(category),CurrencyCode.valueOf(currency),TrackingMode.valueOf(tracking),LocalDate.parse(openedOn),Instant.parse(createdAt),liquid,MarketRegion.valueOf(region),unit,custodyAccountId?.let(UUID::fromString))
internal fun Instrument.entity()=InstrumentEntity(id.toString(),name,category.name,currency.name,tracking.name,openedOn.toString(),createdAt.toString(),liquid,region.name,unit,custodyAccountId?.toString())
internal fun EntryEntity.domain()=InvestmentEntry(UUID.fromString(id),UUID.fromString(instrumentId),TradeKind.valueOf(kind),LocalDate.parse(date),sequence,units.toBigDecimal(),gross?.toBigDecimal(),fees.toBigDecimal(),ratioDenominator.toBigDecimal(),note,Instant.parse(recordedAt))
internal fun InvestmentEntry.entity()=EntryEntity(id.toString(),instrumentId.toString(),kind.name,date.toString(),sequence,units.toPlainString(),gross?.toPlainString(),fees.toPlainString(),ratioDenominator.toPlainString(),note,recordedAt.toString())
internal fun ValuationEntity.domain()=AssetValuation(UUID.fromString(id),UUID.fromString(instrumentId),ValuationKind.valueOf(kind),value.toBigDecimal(),LocalDate.parse(date),revision,Instant.parse(recordedAt),note,includedContractSequence,includedInvestmentSequence,reportedInvested=reportedInvested?.toBigDecimal(),reportedUnits=reportedUnits?.toBigDecimal(),reportedPrice=reportedPrice?.toBigDecimal(),calculatedTotal=calculatedTotal)
internal fun AssetValuation.entity()=ValuationEntity(id.toString(),instrumentId.toString(),kind.name,value.toPlainString(),date.toString(),revision,recordedAt.toString(),note,includedContractSequence,includedInvestmentSequence,reportedInvested?.toPlainString(),reportedUnits?.toPlainString(),reportedPrice?.toPlainString(),calculatedTotal)
internal fun ContractEntity.domain()=FinancialContract(UUID.fromString(instrumentId),openingPrincipal.toBigDecimal(),annualRate.toBigDecimal(),LocalDate.parse(start),LocalDate.parse(end),DepositConvention.valueOf(convention),scheduledInstallment.toBigDecimal(),termMonths)
internal fun FinancialContract.entity()=ContractEntity(instrumentId.toString(),openingPrincipal.toPlainString(),annualRate.toPlainString(),start.toString(),end.toString(),convention.name,scheduledInstallment.toPlainString(),termMonths)
internal fun ContractEventEntity.domain()=ContractEvent(UUID.fromString(id),UUID.fromString(instrumentId),ContractEventKind.valueOf(kind),LocalDate.parse(date),sequence,principal.toBigDecimal(),interest.toBigDecimal(),fees.toBigDecimal(),Instant.parse(recordedAt),note)
internal fun ContractEvent.entity()=ContractEventEntity(id.toString(),instrumentId.toString(),kind.name,date.toString(),sequence,principal.toPlainString(),interest.toPlainString(),fees.toPlainString(),recordedAt.toString(),note)
internal fun CashEntity.domain()=CashMovement(UUID.fromString(id),UUID.fromString(accountId),CurrencyCode.valueOf(currency),LocalDate.parse(date),amount.toBigDecimal(),UUID.fromString(anchorObservationId),applyToAnchor,UUID.fromString(entryId ?: contractEventId ?: requireNotNull(transferId)),Instant.parse(recordedAt),note)
internal fun FxEntity.domain()=FxObservation(UUID.fromString(id),CurrencyCode.valueOf(currency),inrPerUnit.toBigDecimal(),LocalDate.parse(date),revision,Instant.parse(recordedAt),note)
internal fun ReferenceEntity.domain()=StatementReference(UUID.fromString(id),UUID.fromString(accountId),total.toBigDecimal(),LocalDate.parse(date),revision,Instant.parse(recordedAt))

internal suspend fun BankDatabase.readBook():BankBook {
    val banks=bankDao().read()
    val p=portfolioDao()
    val public=publicDao().readPublic();val source=public.observations.associateBy {it.id}
    val valuationSources=public.applications.filter {it.valuationId!=null}.associate {it.valuationId to source[it.observationId]}
    val fxSources=public.applications.filter {it.fxId!=null}.associate {it.fxId to source[it.observationId]}
    return BankBook(banks.map {it.account.domain()},banks.flatMap {row->row.observations.map {it.domain()}},
        PortfolioBook(p.instruments().map {it.domain()},p.entries().map {it.domain()},p.valuations().map {it.domain().copy(source=valuationSources[UUID.fromString(it.id)])},
            p.contracts().map {it.domain()},p.events().map {it.domain()},p.cash().map {it.domain()},p.fx().map {it.domain().copy(source=fxSources[UUID.fromString(it.id)])},p.references().map {it.domain()}),householdDao().readHousehold(),planningDao().readPlanning(),public)
}
