package com.wealthos.database

import com.wealthos.finance.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Reject incompatible records before touching the current vault, beyond SQL/FK validation. */
internal suspend fun validateRestoredBook(db:BankDatabase){
    val b=db.readBook();val p=b.portfolio
    val entriesByInstrument=p.entries.groupBy {it.instrumentId}
    val starts=entriesByInstrument.mapValues {(_,rows)->rows.filter {it.kind==TradeKind.OPENING}.minWithOrNull(compareBy<InvestmentEntry> {it.date}.thenBy {it.sequence})}
    // Markers identify an included event, not an arbitrary count. Index by sequence
    // so backdated records need no chronological-sequence assumption or repeated scans.
    val investmentDates=entriesByInstrument.mapValues {(_,rows)->rows.associate {it.sequence to it.date}}
    val contractDates=p.contractEvents.groupBy {it.instrumentId}.mapValues {(_,rows)->rows.associate {it.sequence to it.date}}
    fun eligibleMarker(marker:Int,date:LocalDate,dates:Map<Int,LocalDate>?) = marker==0 ||
        (marker>0 && dates?.get(marker)?.let {it<=date}==true)
    b.observations.forEach {o->require(o.revision>0 && o.effectiveDate.year>=1900);val a=requireNotNull(b.accounts.find {it.id==o.accountId});require(o.currency==a.currency);Amounts.parse(o.amount.toPlainString(),o.currency)}
    fun money(v:BigDecimal,c:CurrencyCode){require(v>=BigDecimal.ZERO);Amounts.parse(v.toPlainString(),c)}
    p.instruments.forEach {i->
        require(i.openedOn.year>=1900)
        val entries=entriesByInstrument[i.id].orEmpty()
        val start=starts[i.id]
        entries.forEach {e->
            require(e.sequence>0 && e.date>=i.openedOn)
            require(start==null || e.date>start.date || (e.date==start.date && e.sequence>=start.sequence)) {
                "Backup contains a transaction before its known starting position."
            }
            money(e.fees,i.currency);e.gross?.let {money(it,i.currency)}
        }
        val through=(entries.map {it.date}+i.openedOn).max()
        if(i.tracking==TrackingMode.TRANSACTIONS)Holdings.calculate(entries,through) else require(entries.isEmpty())
        if(i.tracking in listOf(TrackingMode.DEPOSIT,TrackingMode.LOAN))require(p.contracts.count {it.instrumentId==i.id && it.start==i.openedOn}==1)
    }

    p.valuations.forEach {v->
        val i=p.instruments.single {it.id==v.instrumentId}
        require(v.revision>0 && v.value>=BigDecimal.ZERO && v.date.year>=1900)
        if(v.kind==ValuationKind.UNIT_PRICE)DecimalInput.parse(v.value.toPlainString())else money(v.value,i.currency)
        val cutover=starts[i.id]?.date
        val preservedSnapshot=i.tracking==TrackingMode.TRANSACTIONS && v.kind==ValuationKind.OBSERVED_TOTAL && cutover!=null && v.date<=cutover
        require(i.tracking!=TrackingMode.LOAN && (preservedSnapshot || v.kind==if(i.tracking==TrackingMode.TRANSACTIONS)ValuationKind.UNIT_PRICE else ValuationKind.OBSERVED_TOTAL))
        require(v.date>=i.openedOn || v.kind==ValuationKind.UNIT_PRICE)
        require(eligibleMarker(v.includedInvestmentSequence,v.date,investmentDates[i.id]) &&
            eligibleMarker(v.includedContractSequence,v.date,contractDates[i.id])) {
            "Backup contains valuation coverage outside its recorded financial history."
        }
        HoldingSnapshots.validate(v,i.currency)
        require(HoldingSnapshots.availableForSnapshot(i.category) || (v.reportedInvested==null && v.reportedUnits==null && v.reportedPrice==null && !v.calculatedTotal))
    }
    p.contracts.forEach {c->Contracts.validate(c);val i=p.instruments.single {it.id==c.instrumentId};require(i.tracking in listOf(TrackingMode.LOAN,TrackingMode.DEPOSIT));val events=p.contractEvents.filter {it.instrumentId==i.id};events.forEach {e->require(e.principal>=BigDecimal.ZERO && e.interest>=BigDecimal.ZERO && e.fees>=BigDecimal.ZERO && e.date>=c.start && e.sequence>0)};if(i.tracking==TrackingMode.LOAN)require(Contracts.loanPrincipal(c,events,(events.map {it.date}+c.start).max())!!>=BigDecimal.ZERO)}
    p.instruments.forEach {i->i.custodyAccountId?.let {id->require(b.accounts.any {it.id==id && it.currency==i.currency && it.kind==AccountKind.BROKER_CASH})}}
    p.contracts.forEach {c->val i=p.instruments.single {it.id==c.instrumentId};money(c.openingPrincipal,i.currency);money(c.scheduledInstallment,i.currency);require(c.openingPrincipal>BigDecimal.ZERO && (i.tracking!=TrackingMode.LOAN || c.termMonths>0));val events=p.contractEvents.filter {it.instrumentId==i.id};events.forEach {e->listOf(e.principal,e.interest,e.fees).forEach {money(it,i.currency)}};RoomPortfolioRepository(db).validateEvents(i,c,events)}
    val cash=db.portfolioDao().cash()
    cash.forEach {c->
        val account=b.accounts.single {it.id.toString()==c.accountId};val anchor=b.observations.single {it.id.toString()==c.anchorObservationId}
        require(c.currency==account.currency.name && anchor.accountId==account.id);Amounts.parse(c.amount,account.currency)
        val date=LocalDate.parse(c.date);require(c.applyToAnchor || date<=anchor.effectiveDate)
        val event=c.entryId?.let {id->p.entries.single {it.id.toString()==id}};val contract=c.contractEventId?.let {id->p.contractEvents.single {it.id.toString()==id}}
        if(event!=null){require(cash.count {it.entryId==c.entryId}==1);val i=p.instruments.single {it.id==event.instrumentId};require(i.currency==account.currency && date==event.date && Holdings.cash(event).compareTo(c.amount.toBigDecimal())==0 && c.amount.toBigDecimal().signum()!=0)}
        if(contract!=null){require(cash.count {it.contractEventId==c.contractEventId}==1);val i=p.instruments.single {it.id==contract.instrumentId};require(i.currency==account.currency && date==contract.date && Contracts.cash(contract).compareTo(c.amount.toBigDecimal())==0 && c.amount.toBigDecimal().signum()!=0)}
    }
    db.portfolioDao().transfers().forEach {t->val currency=CurrencyCode.valueOf(t.currency);val amount=t.amount.toBigDecimal();money(amount,currency);require(amount>BigDecimal.ZERO);val legs=cash.filter {it.transferId==t.id};require(legs.size==2 && legs.map {it.accountId}.distinct().size==2 && legs.all {it.currency==t.currency && it.date==t.date});require(legs.map {it.amount.toBigDecimal()}.sorted().zip(listOf(-amount,amount)).all {(a,b)->a.compareTo(b)==0})}
    p.references.forEach {r->val a=b.accounts.single {it.id==r.accountId};require(a.kind==AccountKind.BROKER_CASH && r.revision>0);money(r.total,a.currency)}
    p.fx.forEach {require(it.currency!=CurrencyCode.INR && it.revision>0);DecimalInput.positive(it.inrPerUnit.toPlainString())}
    b.publicData.observations.forEach {require(it.revision>0 && Regex("[0-9a-f]{64}").matches(it.fingerprint));PublicRules.validate(it.datum,it.retrievedAt)}
    b.publicData.applications.forEach {a->val source=b.publicData.observations.single {it.id==a.observationId}.datum;require((a.valuationId==null)!=(a.fxId==null));if(a.fxId!=null){val fx=p.fx.single {it.id==a.fxId};require(source.kind==PublicKind.FX && source.currency==fx.currency && source.date==fx.date && source.value.compareTo(fx.inrPerUnit)==0)}else{val value=p.valuations.single {it.id==a.valuationId};require(value.instrumentId==a.instrumentId && source.kind==PublicKind.NAV && value.kind==ValuationKind.UNIT_PRICE && source.date==value.date && source.value.compareTo(value.value)==0)}}
    db.bankDao().audits().forEach {UUID.fromString(it.id);java.time.Instant.parse(it.recordedAt)}
    db.publicDao().settings()?.let {require(it.id==1)}
    val h=b.household
    h.rates.forEach {r->val s=h.sources.single {it.id==r.sourceId};require(r.revision>0 && r.effectiveFrom>=s.startedOn);money(r.monthlyNet,s.currency)}
    h.income.forEach {r->val s=h.sources.single {it.id==r.sourceId};require(r.revision>0 && (r.gross==null)==(r.deductions==null));money(r.net,s.currency);r.gross?.let {money(it,s.currency);money(r.deductions!!,s.currency);require(s.kind==IncomeKind.SALARY && (it-r.deductions!!).compareTo(r.net)==0)}}
    h.cashFlows.forEach {r->require(r.revision>0);listOf(r.essential,r.discretionary,r.premiums,r.loanPrincipal,r.loanInterest,r.otherOutflows,r.investments).forEach {money(it,r.currency)};if(r.incomeMode==IncomeMode.MANUAL_TOTAL)money(requireNotNull(r.manualIncome),r.currency)else require(r.manualIncome==null)}
    h.emergencyTargets.forEach {require(it.revision>0 && it.targetInr>BigDecimal.ZERO);money(it.targetInr,CurrencyCode.INR)}
    h.allocations.forEach {r->require(r.revision>0);val currency=r.accountId?.let {id->b.accounts.single {it.id==id}.currency} ?: p.instruments.single {it.id==r.instrumentId}.currency;money(r.nativeAmount,currency)}
    h.terms.forEach {t->val policy=h.policies.single {it.id==t.policyId};require(t.revision>0 && t.validUntil>=t.validFrom);t.cover?.let {money(it,policy.currency);require(it>BigDecimal.ZERO)};t.premium?.let {money(it,policy.currency)}}
    h.settlements.forEach {r->require(r.revision>0);money(r.amount,h.policies.single {it.id==r.policyId}.currency)}
    b.planning.scenarios.forEach {s->require(s.revision>0);Forecasts.validate(s)}
    b.planning.goals.forEach {g->require(g.revision>0 && g.target>BigDecimal.ZERO);money(g.target,g.currency);money(g.allocated,g.currency);DecimalMath.rate(g.annualReturn);DecimalMath.rate(g.inflation)}
    b.planning.assets.forEach {a->DecimalMath.rate(a.annualReturn);money(a.monthlyAmount,p.instruments.single {it.id==a.instrumentId}.currency)}
    b.planning.fx.forEach {require(it.currency!=CurrencyCode.INR);DecimalMath.rate(it.annualChange)}
    b.planning.withdrawals.forEach {w->require(w.month in 1..b.planning.scenarios.single {it.id==w.scenarioId}.months && w.amount>BigDecimal.ZERO);money(w.amount,p.instruments.find {it.id==w.instrumentId}?.currency ?: CurrencyCode.INR)}
}
