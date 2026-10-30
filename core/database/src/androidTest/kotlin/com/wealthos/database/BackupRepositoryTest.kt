package com.wealthos.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import com.wealthos.security.*
import java.io.File
import java.math.BigDecimal
import java.time.*
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class BackupRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="backup-test-${UUID.randomUUID()}.db";private val key=ByteArray(32).also(java.security.SecureRandom()::nextBytes)
    private val safety=File(context.noBackupFilesDir,"test-safety-${UUID.randomUUID()}")
    private val day=LocalDate.parse("2026-10-28");private val at=Instant.parse("2026-10-28T12:00:00Z");private val clock=Clock.fixed(at,ZoneOffset.UTC)
    private val password="Fictional portable passphrase 2026".toCharArray()
    private lateinit var db:BankDatabase;private lateinit var backup:RoomBackupRepository
    private val zero=BigDecimal.ZERO
    @Before fun open(){db=BankDatabase.open(context,key,name);backup=RoomBackupRepository(context,db,clock,safety)}
    @After fun close(){db.close();context.deleteDatabase(name);safety.listFiles()?.forEach {it.delete()};safety.delete();password.fill('\u0000');key.fill(0)}
    private suspend fun snapshot()=backup.snapshot()
    private fun seal(s:VaultSnapshot)=BackupEnvelope.encrypt(SnapshotCodec.encode(s),password)
    private suspend fun bank(amount:String="1234.50")=RoomBankRepository(db,clock).addAccount("Fictional bank தமிழ்","Test",CurrencyCode.INR,AccountKind.SAVINGS,amount.toBigDecimal(),day)
    private suspend fun populateAllTables(){
        val row=StatementInputRow(1,day,null,"Fictional statement shop","", "10.25".toBigDecimal(),StatementDirection.DEBIT)
        RoomStatementRepository(db,clock).save(ParsedStatement(StatementKind.HDFC_BANK,"a".repeat(64),day,day,listOf(row)),"Fictional statement account",YearMonth.from(day),mapOf(1 to SpendingCategory.ESSENTIAL),emptySet(),true)
        val bank=RoomBankRepository(db,clock);val p=RoomPortfolioRepository(db,clock);val h=RoomHouseholdRepository(db,clock);val planning=RoomPlanningRepository(db,clock);val public=RoomPublicRepository(db,clock)
        val cash=bank.addAccount("Fictional savings தமிழ்","Test",CurrencyCode.INR,AccountKind.SAVINGS,"10000.25".toBigDecimal(),day);bank.recordBalance(cash,"10001.25".toBigDecimal(),day,"Keep correction history")
        val reserve=bank.addAccount("Fictional reserve","",CurrencyCode.INR,AccountKind.CURRENT,"2000.00".toBigDecimal(),day)
        val broker=bank.addAccount("Fictional broker cash","",CurrencyCode.INR,AccountKind.BROKER_CASH,zero,day)
        p.transfer(CashLink(cash,true),CashLink(reserve,true),"10.00".toBigDecimal(),day,"Fictional internal transfer")
        val fund=Instrument(UUID.randomUUID(),"Fictional fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.TRANSACTIONS,day,at,true,custodyAccountId=broker)
        p.addInstrument(NewInstrument(fund,"5.00".toBigDecimal(),"400.00".toBigDecimal(),"50.123456789012".toBigDecimal()))
        p.addEntry(InvestmentEntry(UUID.randomUUID(),fund.id,TradeKind.BUY,day,0,"1.00".toBigDecimal(),"50.00".toBigDecimal(),"1.00".toBigDecimal(),recordedAt=at),CashLink(cash,true))
        val fd=Instrument(UUID.randomUUID(),"Fictional FD",AssetCategory.FD,CurrencyCode.INR,TrackingMode.DEPOSIT,day,at)
        p.addInstrument(NewInstrument(fd,contract=FinancialContract(fd.id,"1000.00".toBigDecimal(),"0.07000000000001".toBigDecimal(),day,day.plusYears(1))))
        val loan=Instrument(UUID.randomUUID(),"Fictional loan",AssetCategory.LOAN,CurrencyCode.INR,TrackingMode.LOAN,day.minusDays(1),at)
        p.addInstrument(NewInstrument(loan,contract=FinancialContract(loan.id,"1000.00".toBigDecimal(),"0.12".toBigDecimal(),day.minusDays(1),day.plusYears(1),termMonths=12)))
        p.addContractEvent(ContractEvent(UUID.randomUUID(),loan.id,ContractEventKind.REPAYMENT,day,0,"10.00".toBigDecimal(),"1.00".toBigDecimal(),recordedAt=at),CashLink(cash,true))
        p.addFx(CurrencyCode.EUR,"89.123456789012".toBigDecimal(),day,"Fictional FX source");p.addReference(broker,"10000.00".toBigDecimal(),day)
        val source=IncomeSource(UUID.randomUUID(),"Fictional salary",IncomeKind.SALARY,CurrencyCode.INR,day.withDayOfMonth(1),null,at,at);h.saveSource(source,"75000.00".toBigDecimal())
        h.saveIncome(IncomeRecord(UUID.randomUUID(),source.id,YearMonth.from(day),"75000.00".toBigDecimal(),"90000.00".toBigDecimal(),"15000.00".toBigDecimal(),0,at,"Fictional payslip"))
        h.saveCashFlow(MonthlyCashFlow(UUID.randomUUID(),YearMonth.from(day),CurrencyCode.INR,IncomeMode.RECORDED_SOURCES,null,"20000.00".toBigDecimal(),"5000.00".toBigDecimal(),"1000.00".toBigDecimal(),"10.00".toBigDecimal(),"1.00".toBigDecimal(),zero,"51.00".toBigDecimal(),true,true,0,at,"Fictional summary"))
        h.saveTarget(EmergencyTarget(UUID.randomUUID(),day,"30000.00".toBigDecimal(),0,at,"Target"));h.saveAllocation(EmergencyAllocation(UUID.randomUUID(),cash,null,day,"100.00".toBigDecimal(),0,at,"Earmark"))
        val policy=InsurancePolicy(UUID.randomUUID(),"Fictional health","Test insurer",PolicyKind.HEALTH,CurrencyCode.INR,at,at)
        h.savePolicy(policy,InsuranceTerms(UUID.randomUUID(),policy.id,day,day,day.plusYears(1),"1000000.00".toBigDecimal(),"1000.00".toBigDecimal(),PremiumFrequency.MONTHLY,day.plusDays(5),CoverageScope.FAMILY_FLOATER,true,0,at,"Terms"));h.saveSettlement(PremiumSettlement(UUID.randomUUID(),policy.id,day.plusDays(5),day,"1000.00".toBigDecimal(),0,at,"Paid"))
        val s=ForecastScenario(UUID.randomUUID(),UUID.randomUUID(),"Fictional plan",ScenarioKind.BASE,ForecastMode.CONTRIBUTION,day,120,"0.05".toBigDecimal(),"0.03".toBigDecimal(),"0.06".toBigDecimal(),"10.00".toBigDecimal(),"0.05".toBigDecimal(),zero,zero,zero,zero,zero,zero,false,false,0,at,"")
        planning.saveScenario(s,listOf(AssetAssumption(s.id,fund.id,"0.060000000001".toBigDecimal(),"100.00".toBigDecimal())),listOf(FxAssumption(s.id,CurrencyCode.USD,"0.01".toBigDecimal())),listOf(PlannedWithdrawal(UUID.randomUUID(),s.id,3,null,"10.00".toBigDecimal(),"Withdrawal")))
        planning.saveGoal(FinancialGoal(UUID.randomUUID(),UUID.randomUUID(),"Fictional goal",CurrencyCode.INR,"100000.00".toBigDecimal(),day.plusYears(5),true,"5000.00".toBigDecimal(),"0.06".toBigDecimal(),"0.05".toBigDecimal(),0,at,"",day,true))
        public.saveBinding(NavBinding(UUID.randomUUID(),fund.id,"123456","INF000000001",true,0,at));public.setNetwork(true)
        val nav=PublicDatum(PublicProvider.AMFI_FILE,PublicKind.NAV,"NAV:123456","Fictional Direct Growth","91.123456789012".toBigDecimal(),CurrencyCode.INR,"INR per unit",day,PublicProvider.AMFI_FILE.home,PublicValidation.USER_FILE,"Scheme code 123456","Fictional fixture","Closing NAV for source date","INF000000001")
        public.cache(PublicProvider.AMFI_FILE,listOf(nav),at,at,"Fixture quote");public.applyNav(db.readBook().publicData.observations.single().id,fund.id,true)
        val fx=PublicDatum(PublicProvider.ECB,PublicKind.FX,"FX:USD","USD reference","91.123456789012".toBigDecimal(),CurrencyCode.USD,"INR per native unit",day,"https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml",PublicValidation.HTTPS_VALIDATED,"EUR-base cross rate","Fictional fixture","Source date")
        public.cache(PublicProvider.ECB,listOf(fx),at,at,"Fixture FX");public.applyFx(db.readBook().publicData.observations.single {it.datum.kind==PublicKind.FX}.id,false)
    }
    @Test fun everyTableAndDecimalRestoresUnderADifferentKeyWithProvenance()=runTest(timeout=5.minutes) {
        populateAllTables();val original=snapshot();assertEquals(35,original.tables.size);assertTrue(original.tables.all {it.rows.isNotEmpty()});val wealth=WealthValuation.at(db.readBook(),day).netWorthInr
        val file=backup.export(password);db.close();context.deleteDatabase(name);val newKey=ByteArray(32).also(java.security.SecureRandom()::nextBytes);assertFalse(key.contentEquals(newKey));db=BankDatabase.open(context,newKey,name);backup=RoomBackupRepository(context,db,clock,safety)
        val plan=backup.prepare(file,password);assertEquals(0,plan.currentRecords);backup.restore(plan);val after=snapshot()
        original.tables.forEach {t->val a=after.tables.single {it.name==t.name};when(t.name){"audit_events"->{assertTrue(a.rows.containsAll(t.rows));assertEquals(t.rows.size+1,a.rows.size)};"public_settings"->{val network=t.columns.indexOf("networkEnabled");assertEquals(t.rows.map {r->r.toMutableList().also {it[network]=Cell.Integer(0)}} ,a.rows)};else->assertEquals("Preserve ${t.name}",t,a)}}
        val b=db.readBook();assertFalse(b.publicData.networkEnabled);assertEquals(0,wealth!!.compareTo(WealthValuation.at(b,day).netWorthInr!!));assertEquals(2,b.publicData.applications.size);assertTrue(b.portfolio.valuations.any {it.source!=null});assertTrue(String(context.getDatabasePath(name).readBytes().take(16).toByteArray())!="SQLite format 3\u0000");newKey.fill(0)
    }
    @Test fun independentUnicodeCryptoFixtureWorksOnAndroid(){val lines=InstrumentationRegistry.getInstrumentation().context.assets.open("portable-unicode.fixture").bufferedReader().readLines();assertEquals(lines[1],String(BackupEnvelope.decrypt(Base64.getDecoder().decode(lines[0]),lines[2].toCharArray()),Charsets.UTF_8))}
    @Test fun incorrectPasswordAndDamagedFileNeverChangeCurrentRecords()=runTest(timeout=5.minutes) {bank();val before=snapshot();val file=backup.export(password);try{backup.prepare(file,"Different strong passphrase".toCharArray());fail()}catch(_:IllegalArgumentException){};file[file.lastIndex]=(file.last().toInt() xor 1).toByte();try{backup.prepare(file,password);fail()}catch(_:IllegalArgumentException){};assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty())}
    @Test fun liveChangesAfterPreviewRejectReplacementWithoutSafetyOverwrite()=runTest(timeout=5.minutes) {bank();val file=backup.export(password);val plan=backup.prepare(file,password);bank("999.00");val before=snapshot();try{backup.restore(plan);fail()}catch(_:IllegalArgumentException){};assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty());assertTrue(plan.closed)}
    @Test fun partialInsertFailureRollsBackAllCurrentRecords()=runTest(timeout=5.minutes) {bank();val file=backup.export(password);bank("77.00");val before=snapshot();val plan=backup.prepare(file,password);db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore BEFORE INSERT ON bank_accounts BEGIN SELECT RAISE(ABORT,'injected'); END");try{backup.restore(plan);fail()}catch(_:android.database.SQLException){};assertEquals(before.tables,snapshot().tables);assertEquals(1,backup.safetyBackups().size)}
    @Test fun auditFailureRollsBackReplacementAndSafetyCopyContainsPreviousVault()=runTest(timeout=5.minutes) {bank();val file=backup.export(password);bank("88.00");val before=snapshot();val plan=backup.prepare(file,password);db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_restore_audit BEFORE INSERT ON audit_events WHEN NEW.action='RESTORE_BACKUP' BEGIN SELECT RAISE(ABORT,'injected'); END");try{backup.restore(plan);fail()}catch(_:android.database.SQLException){};assertEquals(before.tables,snapshot().tables);val copy=backup.readSafety(backup.safetyBackups().single().id);assertEquals(before.tables,SnapshotCodec.decode(BackupEnvelope.decrypt(copy,password)).tables)}
    @Test fun missingTablesWrongTypesDuplicateKeysAndExponentBombAreRejectedInStaging()=runTest(timeout=5.minutes) {
        bank();val s=snapshot();val b=s.tables.single {it.name=="balance_observations"};val amount=b.columns.indexOf("amount")
        val variants=listOf(s.copy(tables=s.tables.filter {it.name!="audit_events"}),s.copy(tables=s.tables.map {if(it.name==b.name)it.copy(rows=it.rows+it.rows)else it}),s.copy(tables=s.tables.map {if(it.name==b.name)it.copy(rows=it.rows.map {r->r.toMutableList().also {it[amount]=Cell.Integer(10)}})else it}),s.copy(tables=s.tables.map {if(it.name==b.name)it.copy(rows=it.rows.map {r->r.toMutableList().also {it[amount]=Cell.Text("1E+2147483647")}})else it}))
        variants.forEach {bad->try{backup.prepare(seal(bad),password);fail()}catch(_:Exception){};assertEquals(s.tables,snapshot().tables)}
    }
    @Test fun missingParentAndInvalidFinancialContentsNeverReachLiveVault()=runTest(timeout=5.minutes) {populateAllTables();val s=snapshot();val bad=s.copy(tables=s.tables.map {if(it.name=="income_records")it.copy(rows=it.rows.map {r->r.toMutableList().also {row->row[it.columns.indexOf("net")]=Cell.Text("-1.00")}})else it});try{backup.prepare(seal(bad),password);fail()}catch(_:IllegalArgumentException){};val orphan=s.copy(tables=s.tables.map {if(it.name=="balance_observations")it.copy(rows=it.rows.map {r->r.toMutableList().also {row->row[it.columns.indexOf("accountId")]=Cell.Text(UUID.randomUUID().toString())}})else it});try{backup.prepare(seal(orphan),password);fail()}catch(_:android.database.SQLException){};assertEquals(s.tables,snapshot().tables)}
    @Test fun safetyCopyIoFailurePreservesVault()=runTest(timeout=5.minutes) {bank();val file=backup.export(password);safety.writeText("Not a directory");val plan=backup.prepare(file,password);val before=snapshot();try{backup.restore(plan);fail()}catch(_:IllegalArgumentException){};assertEquals(before.tables,snapshot().tables)}
    @Test fun cancelledRestorePreservesRecords()=runTest(timeout=5.minutes) {bank();val plan=backup.prepare(backup.export(password),password);val before=snapshot();val task=launch {backup.restore(plan)};task.cancel();task.join();plan.close();assertEquals(before.tables,snapshot().tables)}
    @Test fun unknownSchemaIsRejectedAndSafetyCopiesAreRetained()=runTest(timeout=5.minutes) {bank();val original=snapshot();try{backup.prepare(seal(original.copy(schema=999)),password);fail()}catch(_:IllegalArgumentException){};repeat(2){val plan=backup.prepare(backup.export(password),password);backup.restore(plan)};assertEquals(2,backup.safetyBackups().size);backup.safetyBackups().forEach {assertTrue(BackupEnvelope.decrypt(backup.readSafety(it.id),password).isNotEmpty())}}
    @Test fun schemaSixPortableBackupMigratesWithoutLosingBankHistory()=runTest(timeout=5.minutes) {
        bank();val current=snapshot()
        val newColumns=setOf("reportedInvested","reportedUnits","reportedPrice","calculatedTotal")
        val old=current.copy(schema=6,tables=current.tables.filterNot {it.name.startsWith("statement_") && it.name!="statement_references"}.map {table->
            if(table.name!="asset_valuations")table else {
                val keep=table.columns.indices.filter {table.columns[it] !in newColumns}
                table.copy(columns=keep.map {table.columns[it]},rows=table.rows.map {row->keep.map {row[it]}})
            }
        })
        bank("77.00");val plan=backup.prepare(seal(old),password)
        assertEquals(8,plan.info.schema);backup.restore(plan)
        assertEquals(current.tables.single {it.name=="bank_accounts"},snapshot().tables.single {it.name=="bank_accounts"})
        assertEquals(current.tables.single {it.name=="balance_observations"},snapshot().tables.single {it.name=="balance_observations"})
    }
    @Test fun holdingMetadataAndConvertedHistorySurviveEncryptedPortableRestore()=runTest(timeout=5.minutes) {
        val p=RoomPortfolioRepository(db,clock)
        val i=Instrument(UUID.randomUUID(),"Fictional quick fund",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.OBSERVED,day.minusDays(1),at)
        val value=AssetValuation(UUID.randomUUID(),i.id,ValuationKind.OBSERVED_TOTAL,"1234.56".toBigDecimal(),i.openedOn,0,at,reportedInvested="1000.00".toBigDecimal(),reportedUnits="10.000000000001".toBigDecimal(),reportedPrice="123.456789012345".toBigDecimal())
        p.addInstrument(NewInstrument(i,initialValue=value.value,snapshot=value))
        p.startUnitTracking(i.id,day,BigDecimal.TEN,null,"130.123456789012".toBigDecimal())
        val before=snapshot();val bytes=backup.export(password)
        val plan=backup.prepare(bytes,password);backup.restore(plan)
        val after=snapshot();for(name in listOf("instruments","investment_entries","asset_valuations"))assertEquals(before.tables.single {it.name==name},after.tables.single {it.name==name})
        val book=db.readBook();val upgraded=book.portfolio.instruments.single()
        assertNull(WealthValuation.asset(upgraded,book.portfolio,day).holding!!.remainingCost)
        assertEquals("1234.56",WealthValuation.asset(upgraded,book.portfolio,i.openedOn).nativeValue!!.toPlainString())
    }
    @Test fun invalidHoldingMetadataIsRejectedBeforeCurrentRecordsChange()=runTest(timeout=5.minutes) {
        val p=RoomPortfolioRepository(db,clock);val i=Instrument(UUID.randomUUID(),"Fictional snapshot",AssetCategory.STOCK,CurrencyCode.INR,TrackingMode.OBSERVED,day,at)
        p.addInstrument(NewInstrument(i,initialValue=BigDecimal.TEN))
        val before=snapshot();val table=before.tables.single {it.name=="asset_valuations"}
        for((column,bad) in listOf("reportedUnits" to Cell.Text("-1"),"calculatedTotal" to Cell.Integer(2),"reportedInvested" to Cell.Text("1E+10"))) {
            val index=table.columns.indexOf(column);val corrupt=table.copy(rows=table.rows.map {row->row.toMutableList().also {it[index]=bad}})
            try{backup.prepare(seal(before.copy(tables=before.tables.map {if(it.name==table.name)corrupt else it})),password);fail()}catch(_:IllegalArgumentException){}
            assertEquals(before.tables,snapshot().tables)
        }
    }

    private fun changeCell(s:VaultSnapshot,tableName:String,id:UUID,column:String,value:Cell):VaultSnapshot = s.copy(tables=s.tables.map {table->
        if(table.name!=tableName)table else {
            val identity=table.columns.indexOf("id");val index=table.columns.indexOf(column)
            require(identity>=0 && index>=0 && table.rows.count {it[identity]==Cell.Text(id.toString())}==1)
            table.copy(rows=table.rows.map {row->if(row[identity]!=Cell.Text(id.toString()))row else row.toMutableList().also {it[index]=value}})
        }
    })
    private suspend fun convertedHolding():Instrument {
        bank();val repo=RoomPortfolioRepository(db,clock)
        val i=Instrument(UUID.randomUUID(),"Fictional history boundary",AssetCategory.MUTUAL_FUND,CurrencyCode.INR,TrackingMode.OBSERVED,day.minusDays(2),at)
        repo.addInstrument(NewInstrument(i,initialValue="1000.00".toBigDecimal()))
        repo.startUnitTracking(i.id,day.minusDays(1),BigDecimal.TEN,null,"100".toBigDecimal())
        repo.addEntry(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.BUY,day,0,"5".toBigDecimal(),"500".toBigDecimal(),recordedAt=at))
        return db.readBook().portfolio.instruments.single {it.id==i.id}
    }
    @Test fun restoreRejectsTransactionsBeforeTheKnownStartingPosition()=runTest(timeout=5.minutes) {
        val i=convertedHolding();val before=snapshot();val buy=db.readBook().portfolio.entries.single {it.instrumentId==i.id && it.kind==TradeKind.BUY}
        val invalid=changeCell(before,"investment_entries",buy.id,"date",Cell.Text(i.openedOn.toString()))
        try{backup.prepare(seal(invalid),password).use {fail("Accepted a trade before the known starting position")}}catch(_:IllegalArgumentException){}
        assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty())
    }
    @Test fun restoreRejectsImpossibleValuationCoverageWithoutTouchingLiveRecords()=runTest(timeout=5.minutes) {
        val i=convertedHolding();val repo=RoomPortfolioRepository(db,clock)
        val rd=Instrument(UUID.randomUUID(),"Fictional coverage deposit",AssetCategory.RD,CurrencyCode.INR,TrackingMode.DEPOSIT,i.openedOn,at)
        repo.addInstrument(NewInstrument(rd,initialValue="1000.00".toBigDecimal(),contract=FinancialContract(rd.id,"1000".toBigDecimal(),zero,rd.openedOn,day.plusYears(1))))
        repo.addContractEvent(ContractEvent(UUID.randomUUID(),rd.id,ContractEventKind.CONTRIBUTION,day,0,"500".toBigDecimal(),recordedAt=at))
        repo.addValuation(AssetValuation(UUID.randomUUID(),rd.id,ValuationKind.OBSERVED_TOTAL,"1500.00".toBigDecimal(),day,0,at))
        val p=db.readBook().portfolio
        val quote=p.valuations.single {it.instrumentId==i.id && it.kind==ValuationKind.UNIT_PRICE}
        val snapshot=p.valuations.single {it.instrumentId==i.id && it.kind==ValuationKind.OBSERVED_TOTAL}
        val deposit=p.valuations.single {it.instrumentId==rd.id && it.date==day}
        val earlierDeposit=p.valuations.single {it.instrumentId==rd.id && it.date==rd.openedOn}
        val before=snapshot()
        val variants=listOf(Triple(quote.id,"includedInvestmentSequence",-1),Triple(quote.id,"includedInvestmentSequence",Int.MAX_VALUE),
            Triple(snapshot.id,"includedInvestmentSequence",1),Triple(deposit.id,"includedContractSequence",-1),
            Triple(deposit.id,"includedContractSequence",Int.MAX_VALUE),Triple(earlierDeposit.id,"includedContractSequence",1))
        for((id,column,value) in variants){
            val invalid=changeCell(before,"asset_valuations",id,column,Cell.Integer(value.toLong()))
            try{backup.prepare(seal(invalid),password).use {fail("Accepted impossible valuation coverage: $column=$value")}}catch(_:IllegalArgumentException){}
            assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty())
        }
    }

    @Test fun restoreRejectsSameDayTradeOrderedBeforeTheStartingPosition()=runTest(timeout=5.minutes) {
        val i=convertedHolding();val before=snapshot();val entries=db.readBook().portfolio.entries.filter {it.instrumentId==i.id}
        val opening=entries.single {it.kind==TradeKind.OPENING};val buy=entries.single {it.kind==TradeKind.BUY}
        val quote=db.readBook().portfolio.valuations.single {it.instrumentId==i.id && it.kind==ValuationKind.UNIT_PRICE}
        val reordered=changeCell(changeCell(changeCell(before,"investment_entries",buy.id,"date",Cell.Text(opening.date.toString())),
            "investment_entries",opening.id,"sequence",Cell.Integer(3)),"asset_valuations",quote.id,"includedInvestmentSequence",Cell.Integer(3))
        try{backup.prepare(seal(reordered),password).use {fail("Accepted a same-day trade ordered before its starting position")}}catch(_:IllegalArgumentException){}
        assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty())
    }

    @Test fun backdatedCoveragePreservesValidHistoryButRejectsAnIneligibleSequence()=runTest(timeout=5.minutes) {
        val i=convertedHolding();val repo=RoomPortfolioRepository(db,clock)
        // Sequence 2 is dated later than sequence 3. Numerical range alone is insufficient.
        repo.addEntry(InvestmentEntry(UUID.randomUUID(),i.id,TradeKind.BUY,day.minusDays(1),0,BigDecimal.ONE,"100".toBigDecimal(),recordedAt=at))
        repo.addValuation(AssetValuation(UUID.randomUUID(),i.id,ValuationKind.UNIT_PRICE,"101".toBigDecimal(),day.minusDays(1),0,at,"Fictional backdated quote correction"))
        val quote=db.readBook().portfolio.valuations.single {it.instrumentId==i.id && it.revision==2}
        assertEquals(3,quote.includedInvestmentSequence)
        val before=snapshot()
        backup.prepare(backup.export(password),password).use {assertEquals(before.records,it.info.records)}
        val invalid=changeCell(before,"asset_valuations",quote.id,"includedInvestmentSequence",Cell.Integer(2))
        try{backup.prepare(seal(invalid),password).use {fail("Accepted a sequence whose event occurs after the valuation date")}}catch(_:IllegalArgumentException){}
        assertEquals(before.tables,snapshot().tables);assertTrue(backup.safetyBackups().isEmpty())
    }

}
