package com.wealthos.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object SnapshotMigrations {
    val FROM_6_TO_7=object:Migration(6,7){
        override fun migrate(db:SupportSQLiteDatabase){
            db.execSQL("ALTER TABLE asset_valuations ADD COLUMN reportedInvested TEXT")
            db.execSQL("ALTER TABLE asset_valuations ADD COLUMN reportedUnits TEXT")
            db.execSQL("ALTER TABLE asset_valuations ADD COLUMN reportedPrice TEXT")
            db.execSQL("ALTER TABLE asset_valuations ADD COLUMN calculatedTotal INTEGER NOT NULL DEFAULT 0")
        }
    }
}
