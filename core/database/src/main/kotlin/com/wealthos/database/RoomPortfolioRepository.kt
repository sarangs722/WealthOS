package com.wealthos.database

import androidx.room.withTransaction
import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

class RoomPortfolioRepository(private val db:BankDatabase,private val clock:Clock=Clock.systemDefaultZone()):PortfolioRepository {
    private val dao=db.portfolioDao()
    override suspend fun editInstrument(id:UUID,name:String,unit:String,region:MarketRegion,liquid:Boolean,custodyAccountId:UUID?) = db.withTransaction {
        val book=db.readBook(); val old=instrument(book.portfolio,id)
        custodyAccountId?.let { accountId -> require(book.accounts.any {it.id==accountId && it.kind==AccountKind.BROKER_CASH && it.currency==old.currency}) {"Choose matching brokerage cash."} }
        val updated=old.copy(name=name.trim(),unit=unit.trim(),region=region,liquid=liquid,custodyAccountId=custodyAccountId)
        dao.update(updated.entity())
        db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),id.toString(),"EDIT_INSTRUMENT",clock.instant().toString(),old.toString(),updated.toString()))
    }
    override suspend fun addInstrument(input:NewInstrument):Unit = db.withTransaction {
        val i=input.instrument.copy(createdAt=clock.instant())
        date(i.openedOn)
        val book=db.readBook()
        i.custodyAccountId?.let { id ->
            val account=requireNotNull(book.accounts.find {it.id==id}) {"Custody cash account is unavailable."}
            require(account.kind==AccountKind.BROKER_CASH && account.currency==i.currency) {"Choose a brokerage cash account in the instrument's currency."}
        }
        dao.insert(i.entity())
        audit(i.id,"CREATE_INSTRUMENT",i.toString())
        when(i.tracking) {
            TrackingMode.TRANSACTIONS -> {
                require(input.funding==null) {"Opening positions do not invent cash movements. Record a purchase separately."}
                require(input.openingUnits>=BigDecimal.ZERO)
                DecimalInput.parse(input.openingUnits.toPlainString())
                input.openingCost?.let {money(it,i.currency);require(it>=BigDecimal.ZERO)}
                if(input.openingUnits.signum()>0) {
                    val entry=InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.OPENING,i.openedOn,1,input.openingUnits,input.openingCost,recordedAt=clock.instant(),note="Quick Add opening position; earlier transactions unknown")
                    Holdings.calculate(listOf(entry),i.openedOn)
                    dao.insert(entry.entity()); audit(entry.id,"OPEN_POSITION",entry.toString())
                } else require(input.openingCost?.signum() in listOf(null,0)) {"Zero units cannot have an opening cost."}
            }
            TrackingMode.DEPOSIT, TrackingMode.LOAN -> {
                val c=requireNotNull(input.contract) {"Contract terms are required."}
                require(c.instrumentId==i.id && c.start==i.openedOn)
                Contracts.validate(c); money(c.openingPrincipal,i.currency); money(c.scheduledInstallment,i.currency)
                require(c.openingPrincipal>BigDecimal.ZERO) {"Opening principal must be positive."}
                if(i.tracking==TrackingMode.LOAN) require(c.termMonths>0 && input.funding==null) {"Enter remaining loan months; an opening liability creates no cash."}
                dao.insert(c.entity()); audit(i.id,"CREATE_CONTRACT",c.toString())
                input.funding?.let {link ->
                    require(i.tracking==TrackingMode.DEPOSIT)
                    val event=ContractEvent(UUID.randomUUID(),i.id,ContractEventKind.FUNDING,c.start,1,c.openingPrincipal,recordedAt=clock.instant(),note="Explicit new deposit funding")
                    dao.insert(event.entity()); audit(event.id,"FUND_DEPOSIT",event.toString())
                    cash(link,i.currency,c.start,Contracts.cash(event),event.id,"contract",event.note,book)
                }
            }
            TrackingMode.OBSERVED -> require(input.initialValue!=null && input.funding==null) {"Enter your share of the observed asset value; opening records do not create cash."}
        }
        input.initialValue?.let {amount ->
            require(i.tracking!=TrackingMode.LOAN) {"Use the loan's opening principal."}
            validateValue(i,amount)
            val value=AssetValuation(UUID.randomUUID(),i.id,if(i.tracking==TrackingMode.TRANSACTIONS) ValuationKind.UNIT_PRICE else ValuationKind.OBSERVED_TOTAL,amount,i.openedOn,1,clock.instant(),"Opening manual valuation")
            dao.insert(value.entity());audit(value.id,"VALUE_ASSET",value.toString())
        }
    }

    override suspend fun addEntry(entry:InvestmentEntry,cash:CashLink?):Unit = db.withTransaction {
        val book=db.readBook();val p=book.portfolio
        val i=instrument(p,entry.instrumentId)
        require(i.tracking==TrackingMode.TRANSACTIONS && entry.kind!=TradeKind.OPENING) {"Add a transaction after the opening position."}
        date(entry.date); require(entry.date>=i.openedOn) {"Transactions cannot precede the known opening position."}
        entry.gross?.let {money(it,i.currency)};money(entry.fees,i.currency)
        val entries=p.entries.filter {it.instrumentId==i.id}
        val actual=entry.copy(sequence=(entries.maxOfOrNull {it.sequence} ?: 0)+1,recordedAt=clock.instant())
        Holdings.calculate(entries+actual,LocalDate.now(clock)) // validates the entire future suffix after a backdated event
        val amount=Holdings.cash(actual)
        require(cash==null || amount.signum()!=0) {"This event has no cash movement."}
        dao.insert(actual.entity());audit(actual.id,"INVESTMENT_EVENT",actual.toString())
        cash?.let {cash(it,i.currency,actual.date,amount,actual.id,"entry",actual.kind.label,book)}
    }

    override suspend fun addValuation(value:AssetValuation) = db.withTransaction {
        val p=db.readBook().portfolio;val i=instrument(p,value.instrumentId)
        require(i.tracking!=TrackingMode.LOAN) {"Record actual principal repayments for loans."}
        date(value.date);require(value.date>=i.openedOn || i.tracking==TrackingMode.TRANSACTIONS) {"Observed totals cannot precede the opening date. Unit quotes have their own financial dates."}
        require(value.kind==if(i.tracking==TrackingMode.TRANSACTIONS) ValuationKind.UNIT_PRICE else ValuationKind.OBSERVED_TOTAL)
        validateValue(i,value.value); require(value.note.length<=500)
        val revision=p.valuations.filter {it.instrumentId==i.id && it.date==value.date}.maxOfOrNull {it.revision} ?: 0
        require(revision==0 || value.note.isNotBlank()) {"Explain this same-day valuation correction."}
        val actual=value.copy(revision=revision+1,recordedAt=clock.instant(),
            includedContractSequence=p.contractEvents.filter {it.instrumentId==i.id && it.date<=value.date}.maxOfOrNull {it.sequence} ?: 0,
            includedInvestmentSequence=p.entries.filter {it.instrumentId==i.id && it.date<=value.date}.maxOfOrNull {it.sequence} ?: 0)
        dao.insert(actual.entity());audit(actual.id,"VALUE_ASSET",actual.toString())
    }

    override suspend fun addContractEvent(event:ContractEvent,cash:CashLink?):Unit = db.withTransaction {
        val book=db.readBook();val p=book.portfolio;val i=instrument(p,event.instrumentId)
        val contract=requireNotNull(p.contracts.find {it.instrumentId==i.id}) {"Contract is missing."}
        require(event.kind!=ContractEventKind.FUNDING) {"Opening funding is recorded when creating the contract."}
        date(event.date);require(event.date>=contract.start) {"Event cannot precede the opening date."}
        listOf(event.principal,event.interest,event.fees).forEach {money(it,i.currency);require(it>=BigDecimal.ZERO)}
        require(event.note.length<=500)
        val existing=p.contractEvents.filter {it.instrumentId==i.id}
        val actual=event.copy(sequence=(existing.maxOfOrNull {it.sequence} ?: 0)+1,recordedAt=clock.instant())
        validateEvents(i,contract,existing+actual)
        val amount=Contracts.cash(actual)
        require(cash==null || amount.signum()!=0)
        dao.insert(actual.entity());audit(actual.id,"CONTRACT_EVENT",actual.toString())
        cash?.let {cash(it,i.currency,actual.date,amount,actual.id,"contract",actual.kind.label,book)}
    }

    internal fun validateEvents(i:Instrument,c:FinancialContract,events:List<ContractEvent>) {
        val sorted=events.sortedWith(compareBy<ContractEvent> {it.date}.thenBy {it.sequence})
        var principal=c.openingPrincipal;var closed=false;val seen=mutableListOf<ContractEvent>()
        sorted.forEach {e ->
            require(!closed) {"No event can follow a deposit's closure."}
            when(e.kind) {
                ContractEventKind.FUNDING -> require(e.date==c.start && e.principal.compareTo(c.openingPrincipal)==0)
                ContractEventKind.CONTRIBUTION -> {
                    require(i.category==AssetCategory.RD && e.date<=c.end && e.principal>BigDecimal.ZERO && e.interest.signum()==0) {"Only actual RD installments are supported, on or before maturity."}
                    principal+=e.principal
                }
                ContractEventKind.REPAYMENT -> {
                    require(i.tracking==TrackingMode.LOAN && e.principal<=principal && e.principal+e.interest+e.fees>BigDecimal.ZERO) {"Repayment principal exceeds the outstanding loan or payment is empty."}
                    principal-=e.principal
                }
                ContractEventKind.MATURITY -> {
                    require(i.tracking==TrackingMode.DEPOSIT && e.principal.compareTo(principal)==0 && e.principal+e.interest>=e.fees) {"Closure must return the recorded principal; enter interest/fees separately."}
                    closed=true
                }
                ContractEventKind.INTEREST_PAYOUT -> {
                    require(i.tracking==TrackingMode.DEPOSIT && c.convention==DepositConvention.SIMPLE_ACT_365 && e.principal.signum()==0 && e.interest>BigDecimal.ZERO && e.interest>=e.fees) {"Interim payouts require a simple ACT/365 contract; cumulative contracts pay at closure."}
                    val earned=Contracts.depositValue(c,seen,e.date)!!-principal
                    require(e.interest<=earned) {"Payout exceeds the contractual gross interest accrued so far."}
                }
            }
            seen+=e
        }
    }

    override suspend fun addFx(currency:CurrencyCode,rate:BigDecimal,date:LocalDate,note:String) = db.withTransaction {
        date(date);require(currency!=CurrencyCode.INR && rate>BigDecimal.ZERO && rate<=BigDecimal("1000000000000")) {"Enter a positive INR rate for a foreign currency."}
        DecimalInput.parse(rate.toPlainString());require(note.isNotBlank() && note.length<=500) {"Describe the manual FX source or assumption."}
        val revision=(dao.fx().filter {it.currency==currency.name && it.date==date.toString()}.maxOfOrNull {it.revision} ?: 0)+1
        val id=UUID.randomUUID()
        val fx=FxEntity(id.toString(),currency.name,rate.toPlainString(),date.toString(),revision,clock.instant().toString(),note)
        dao.insert(fx);audit(id,"MANUAL_FX",fx.toString())
    }
    override suspend fun transfer(from:CashLink,to:CashLink,amount:BigDecimal,date:LocalDate,note:String) = db.withTransaction {
        date(date);require(from.accountId!=to.accountId && amount>BigDecimal.ZERO && note.isNotBlank() && note.length<=500)
        val book=db.readBook();val source=requireNotNull(book.accounts.find {it.id==from.accountId})
        val target=requireNotNull(book.accounts.find {it.id==to.accountId})
        require(source.currency==target.currency) {"Internal transfers currently require matching native currencies."}
        money(amount,source.currency)
        val id=UUID.randomUUID();val transfer=TransferEntity(id.toString(),source.currency.name,amount.toPlainString(),date.toString(),clock.instant().toString(),note)
        dao.insert(transfer)
        cash(from,source.currency,date,-amount,id,"transfer",note,book)
        cash(to,target.currency,date,amount,id,"transfer",note,book)
        audit(id,"INTERNAL_TRANSFER",transfer.toString())
    }
    override suspend fun addReference(accountId:UUID,total:BigDecimal,date:LocalDate) = db.withTransaction {
        date(date);val account=requireNotNull(db.bankDao().account(accountId.toString())) {"Cash account is unavailable."}.domain()
        require(account.kind==AccountKind.BROKER_CASH && total>=BigDecimal.ZERO) {"Statement references are for brokerage cash accounts."}
        money(total,account.currency)
        val revision=(dao.references().filter {it.accountId==accountId.toString() && it.date==date.toString()}.maxOfOrNull {it.revision} ?: 0)+1
        val id=UUID.randomUUID();val reference=ReferenceEntity(id.toString(),accountId.toString(),total.toPlainString(),date.toString(),revision,clock.instant().toString())
        dao.insert(reference);audit(id,"STATEMENT_REFERENCE",reference.toString())
    }
    private suspend fun cash(link:CashLink,currency:CurrencyCode,date:LocalDate,amount:BigDecimal,source:UUID,type:String,note:String,book:BankBook) {
        val account=requireNotNull(book.accounts.find {it.id==link.accountId}) {"Linked cash account is unavailable."}
        require(account.currency==currency) {"Cash account must use the event's native currency. Record actual FX separately."}
        val snapshot=requireNotNull(book.observations.filter {it.accountId==account.id && it.effectiveDate<=LocalDate.now(clock)}
            .maxWithOrNull(compareBy<BalanceObservation> {it.effectiveDate}.thenBy {it.revision})) {"Add a dated cash balance before linking movements."}
        require(link.applyToSnapshot || date<=snapshot.effectiveDate) {"A balance dated before the event cannot already include it. Choose provisional adjustment."}
        val movement=CashEntity(UUID.randomUUID().toString(),account.id.toString(),currency.name,date.toString(),amount.toPlainString(),snapshot.id.toString(),link.applyToSnapshot,
            source.toString().takeIf {type=="entry"},source.toString().takeIf {type=="contract"},source.toString().takeIf {type=="transfer"},clock.instant().toString(),note)
        dao.insert(movement)
    }
    private fun instrument(book:PortfolioBook,id:UUID)=requireNotNull(book.instruments.find {it.id==id}) {"Instrument is unavailable."}
    private fun date(value:LocalDate) {require(value.year in 1900..9999 && value<=LocalDate.now(clock)) {"Use an effective date from 1900 through today."}}
    private fun money(value:BigDecimal,currency:CurrencyCode) {Amounts.parse(value.toPlainString(),currency)}
    private fun validateValue(i:Instrument,value:BigDecimal) {
        require(value>=BigDecimal.ZERO) {"Valuations cannot be negative."}
        if(i.tracking==TrackingMode.TRANSACTIONS) DecimalInput.parse(value.toPlainString()) else money(value,i.currency)
    }
    private suspend fun audit(id:UUID,action:String,value:String) = db.bankDao().insertAudit(AuditEntity(UUID.randomUUID().toString(),id.toString(),action,clock.instant().toString(),null,value))
}
