package com.wealthos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive only: existing accounts, holdings, household records and key material are unchanged. */
object PlanningMigrations {
    val FROM_4_TO_5=object:Migration(4,5){
        override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS `forecast_scenarios` (`id` TEXT NOT NULL, `scenarioKey` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `mode` TEXT NOT NULL, `asOf` TEXT NOT NULL, `months` INTEGER NOT NULL, `inflation` TEXT NOT NULL, `cashReturn` TEXT NOT NULL, `investmentReturn` TEXT NOT NULL, `monthlyContribution` TEXT NOT NULL, `stepUp` TEXT NOT NULL, `salary` TEXT NOT NULL, `salaryGrowth` TEXT NOT NULL, `essential` TEXT NOT NULL, `discretionary` TEXT NOT NULL, `expenseInflation` TEXT NOT NULL, `premiums` TEXT NOT NULL, `reinvestDeposits` INTEGER NOT NULL, `includeGoals` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_forecast_scenarios_scenarioKey_revision` ON `forecast_scenarios` (`scenarioKey`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `forecast_assets` (`scenarioId` TEXT NOT NULL, `instrumentId` TEXT NOT NULL, `annualReturn` TEXT NOT NULL, `monthlyAmount` TEXT NOT NULL, PRIMARY KEY(`scenarioId`, `instrumentId`), FOREIGN KEY(`scenarioId`) REFERENCES `forecast_scenarios`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_assets_instrumentId` ON `forecast_assets` (`instrumentId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `forecast_fx` (`scenarioId` TEXT NOT NULL, `currency` TEXT NOT NULL, `annualChange` TEXT NOT NULL, PRIMARY KEY(`scenarioId`, `currency`), FOREIGN KEY(`scenarioId`) REFERENCES `forecast_scenarios`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `forecast_withdrawals` (`id` TEXT NOT NULL, `scenarioId` TEXT NOT NULL, `month` INTEGER NOT NULL, `instrumentId` TEXT, `amount` TEXT NOT NULL, `label` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`scenarioId`) REFERENCES `forecast_scenarios`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_withdrawals_scenarioId` ON `forecast_withdrawals` (`scenarioId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_forecast_withdrawals_instrumentId` ON `forecast_withdrawals` (`instrumentId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `financial_goals` (`id` TEXT NOT NULL, `goalKey` TEXT NOT NULL, `name` TEXT NOT NULL, `currency` TEXT NOT NULL, `target` TEXT NOT NULL, `due` TEXT NOT NULL, `targetInTodayMoney` INTEGER NOT NULL, `allocated` TEXT NOT NULL, `annualReturn` TEXT NOT NULL, `inflation` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, `basisDate` TEXT NOT NULL, `active` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_goals_goalKey_revision` ON `financial_goals` (`goalKey`, `revision`)")
        }
    }
}
