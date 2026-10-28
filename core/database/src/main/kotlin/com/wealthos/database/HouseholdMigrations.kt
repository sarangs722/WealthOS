package com.wealthos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive schema 3→4 migration. Existing financial tables and keys are preserved. */
object HouseholdMigrations {
    val FROM_3_TO_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `income_sources` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL, `startedOn` TEXT NOT NULL, `endedOn` TEXT, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `income_rates` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `effectiveFrom` TEXT NOT NULL, `monthlyNet` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sourceId`) REFERENCES `income_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_income_rates_sourceId_effectiveFrom_revision` ON `income_rates` (`sourceId`, `effectiveFrom`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `income_records` (`id` TEXT NOT NULL, `sourceId` TEXT NOT NULL, `month` TEXT NOT NULL, `net` TEXT NOT NULL, `gross` TEXT, `deductions` TEXT, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`sourceId`) REFERENCES `income_sources`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_income_records_sourceId_month_revision` ON `income_records` (`sourceId`, `month`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `monthly_cashflows` (`id` TEXT NOT NULL, `month` TEXT NOT NULL, `currency` TEXT NOT NULL, `incomeMode` TEXT NOT NULL, `manualIncome` TEXT, `essential` TEXT NOT NULL, `discretionary` TEXT NOT NULL, `premiums` TEXT NOT NULL, `loanPrincipal` TEXT NOT NULL, `loanInterest` TEXT NOT NULL, `otherOutflows` TEXT NOT NULL, `investments` TEXT NOT NULL, `incomeConfirmed` INTEGER NOT NULL, `expensesConfirmed` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_cashflows_month_currency_revision` ON `monthly_cashflows` (`month`, `currency`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_targets` (`id` TEXT NOT NULL, `date` TEXT NOT NULL, `targetInr` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_emergency_targets_date_revision` ON `emergency_targets` (`date`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `emergency_allocations` (`id` TEXT NOT NULL, `accountId` TEXT, `instrumentId` TEXT, `date` TEXT NOT NULL, `nativeAmount` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_emergency_allocations_accountId_date_revision` ON `emergency_allocations` (`accountId`, `date`, `revision`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_emergency_allocations_instrumentId_date_revision` ON `emergency_allocations` (`instrumentId`, `date`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `insurance_policies` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `insurer` TEXT NOT NULL, `kind` TEXT NOT NULL, `currency` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `insurance_terms` (`id` TEXT NOT NULL, `policyId` TEXT NOT NULL, `effectiveFrom` TEXT NOT NULL, `validFrom` TEXT NOT NULL, `validUntil` TEXT NOT NULL, `cover` TEXT, `premium` TEXT, `frequency` TEXT NOT NULL, `anchorDue` TEXT, `scope` TEXT NOT NULL, `active` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, `lastPremiumDue` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`policyId`) REFERENCES `insurance_policies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_insurance_terms_policyId_effectiveFrom_revision` ON `insurance_terms` (`policyId`, `effectiveFrom`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `premium_settlements` (`id` TEXT NOT NULL, `policyId` TEXT NOT NULL, `dueOn` TEXT NOT NULL, `paidOn` TEXT NOT NULL, `amount` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`policyId`) REFERENCES `insurance_policies`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_premium_settlements_policyId_dueOn_revision` ON `premium_settlements` (`policyId`, `dueOn`, `revision`)")
        }
    }
}
