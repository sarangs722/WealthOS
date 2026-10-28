package com.wealthos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive migration from the populated Milestone 1 vault. SQL matches exported Room schema 3. */
object PortfolioMigrations {
    val FROM_2_TO_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `instruments` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `currency` TEXT NOT NULL, `tracking` TEXT NOT NULL, `openedOn` TEXT NOT NULL, `createdAt` TEXT NOT NULL, `liquid` INTEGER NOT NULL, `region` TEXT NOT NULL, `unit` TEXT NOT NULL, `custodyAccountId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`custodyAccountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_instruments_custodyAccountId` ON `instruments` (`custodyAccountId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `investment_entries` (`id` TEXT NOT NULL, `instrumentId` TEXT NOT NULL, `kind` TEXT NOT NULL, `date` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `units` TEXT NOT NULL, `gross` TEXT, `fees` TEXT NOT NULL, `ratioDenominator` TEXT NOT NULL, `note` TEXT NOT NULL, `recordedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_investment_entries_instrumentId_sequence` ON `investment_entries` (`instrumentId`, `sequence`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `asset_valuations` (`id` TEXT NOT NULL, `instrumentId` TEXT NOT NULL, `kind` TEXT NOT NULL, `value` TEXT NOT NULL, `date` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, `includedContractSequence` INTEGER NOT NULL DEFAULT 0, `includedInvestmentSequence` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`), FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_asset_valuations_instrumentId_date_revision` ON `asset_valuations` (`instrumentId`, `date`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `financial_contracts` (`instrumentId` TEXT NOT NULL, `openingPrincipal` TEXT NOT NULL, `annualRate` TEXT NOT NULL, `start` TEXT NOT NULL, `end` TEXT NOT NULL, `convention` TEXT NOT NULL, `scheduledInstallment` TEXT NOT NULL, `termMonths` INTEGER NOT NULL, PRIMARY KEY(`instrumentId`), FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE TABLE IF NOT EXISTS `contract_events` (`id` TEXT NOT NULL, `instrumentId` TEXT NOT NULL, `kind` TEXT NOT NULL, `date` TEXT NOT NULL, `sequence` INTEGER NOT NULL, `principal` TEXT NOT NULL, `interest` TEXT NOT NULL, `fees` TEXT NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_contract_events_instrumentId_sequence` ON `contract_events` (`instrumentId`, `sequence`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cash_movements` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `currency` TEXT NOT NULL, `date` TEXT NOT NULL, `amount` TEXT NOT NULL, `anchorObservationId` TEXT NOT NULL, `applyToAnchor` INTEGER NOT NULL, `entryId` TEXT, `contractEventId` TEXT, `transferId` TEXT, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`anchorObservationId`) REFERENCES `balance_observations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`entryId`) REFERENCES `investment_entries`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`contractEventId`) REFERENCES `contract_events`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`transferId`) REFERENCES `cash_transfers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_accountId` ON `cash_movements` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_anchorObservationId` ON `cash_movements` (`anchorObservationId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_entryId` ON `cash_movements` (`entryId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_contractEventId` ON `cash_movements` (`contractEventId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_movements_transferId` ON `cash_movements` (`transferId`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `cash_transfers` (`id` TEXT NOT NULL, `currency` TEXT NOT NULL, `amount` TEXT NOT NULL, `date` TEXT NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `fx_observations` (`id` TEXT NOT NULL, `currency` TEXT NOT NULL, `inrPerUnit` TEXT NOT NULL, `date` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, `note` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_fx_observations_currency_date_revision` ON `fx_observations` (`currency`, `date`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `statement_references` (`id` TEXT NOT NULL, `accountId` TEXT NOT NULL, `total` TEXT NOT NULL, `date` TEXT NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`accountId`) REFERENCES `bank_accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_statement_references_accountId_date_revision` ON `statement_references` (`accountId`, `date`, `revision`)")
        }
    }
}
