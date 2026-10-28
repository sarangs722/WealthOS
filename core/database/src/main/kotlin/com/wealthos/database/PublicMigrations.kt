package com.wealthos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive schema 5→6. Public refreshes default OFF; existing financial records/keys are unchanged. */
object PublicMigrations {
    val FROM_5_TO_6=object:Migration(5,6){
        override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("CREATE TABLE IF NOT EXISTS `public_settings` (`id` INTEGER NOT NULL, `networkEnabled` INTEGER NOT NULL, `updatedAt` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("INSERT OR IGNORE INTO `public_settings` (`id`, `networkEnabled`, `updatedAt`) VALUES (1, 0, '1970-01-01T00:00:00Z')")
            db.execSQL("CREATE TABLE IF NOT EXISTS `public_observations` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `kind` TEXT NOT NULL, `series` TEXT NOT NULL, `label` TEXT NOT NULL, `value` TEXT NOT NULL, `currency` TEXT, `units` TEXT NOT NULL, `date` TEXT NOT NULL, `sourceUrl` TEXT NOT NULL, `validation` TEXT NOT NULL, `basis` TEXT NOT NULL, `details` TEXT NOT NULL, `publication` TEXT NOT NULL, `identity` TEXT NOT NULL, `retrievedAt` TEXT NOT NULL, `revision` INTEGER NOT NULL, `fingerprint` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_public_observations_provider_series_date_revision` ON `public_observations` (`provider`, `series`, `date`, `revision`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_public_observations_provider_series_date_fingerprint` ON `public_observations` (`provider`, `series`, `date`, `fingerprint`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `nav_bindings` (`id` TEXT NOT NULL, `instrumentId` TEXT NOT NULL, `schemeCode` TEXT NOT NULL, `isin` TEXT NOT NULL, `active` INTEGER NOT NULL, `revision` INTEGER NOT NULL, `recordedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_nav_bindings_instrumentId_revision` ON `nav_bindings` (`instrumentId`, `revision`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `public_refresh_runs` (`id` TEXT NOT NULL, `provider` TEXT NOT NULL, `attemptedAt` TEXT NOT NULL, `finishedAt` TEXT NOT NULL, `success` INTEGER NOT NULL, `count` INTEGER NOT NULL, `message` TEXT NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_public_refresh_runs_provider_attemptedAt` ON `public_refresh_runs` (`provider`, `attemptedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `public_applications` (`id` TEXT NOT NULL, `observationId` TEXT NOT NULL, `targetKey` TEXT NOT NULL, `instrumentId` TEXT, `valuationId` TEXT, `fxId` TEXT, `appliedAt` TEXT NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`observationId`) REFERENCES `public_observations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`instrumentId`) REFERENCES `instruments`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`valuationId`) REFERENCES `asset_valuations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(`fxId`) REFERENCES `fx_observations`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_public_applications_observationId_targetKey` ON `public_applications` (`observationId`, `targetKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_public_applications_instrumentId` ON `public_applications` (`instrumentId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_public_applications_valuationId` ON `public_applications` (`valuationId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_public_applications_fxId` ON `public_applications` (`fxId`)")
        }
    }
}
