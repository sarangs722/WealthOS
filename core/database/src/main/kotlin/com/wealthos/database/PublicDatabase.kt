package com.wealthos.database

import androidx.room.*
import com.wealthos.finance.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity(tableName="public_settings")
data class PublicSettingsEntity(@PrimaryKey val id:Int,val networkEnabled:Boolean,val updatedAt:String)
@Entity(tableName="public_observations",indices=[Index(value=["provider","series","date","revision"],unique=true),Index(value=["provider","series","date","fingerprint"])])
data class PublicObservationEntity(@PrimaryKey val id:String,val provider:String,val kind:String,val series:String,val label:String,val value:String,val currency:String?,val units:String,
    val date:String,val sourceUrl:String,val validation:String,val basis:String,val details:String,val publication:String,val identity:String,val retrievedAt:String,val revision:Int,val fingerprint:String)
@Entity(tableName="nav_bindings",foreignKeys=[ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT)],indices=[Index(value=["instrumentId","revision"],unique=true)])
data class NavBindingEntity(@PrimaryKey val id:String,val instrumentId:String,val schemeCode:String,val isin:String,val active:Boolean,val revision:Int,val recordedAt:String)
@Entity(tableName="public_refresh_runs",indices=[Index(value=["provider","attemptedAt"])])
data class PublicRunEntity(@PrimaryKey val id:String,val provider:String,val attemptedAt:String,val finishedAt:String,val success:Boolean,val count:Int,val message:String)
@Entity(tableName="public_applications",foreignKeys=[ForeignKey(entity=PublicObservationEntity::class,parentColumns=["id"],childColumns=["observationId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=InstrumentEntity::class,parentColumns=["id"],childColumns=["instrumentId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=ValuationEntity::class,parentColumns=["id"],childColumns=["valuationId"],onDelete=ForeignKey.RESTRICT),ForeignKey(entity=FxEntity::class,parentColumns=["id"],childColumns=["fxId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index(value=["observationId","targetKey"],unique=true),Index("instrumentId"),Index(value=["valuationId"],unique=true),Index(value=["fxId"],unique=true)])
data class PublicApplicationEntity(@PrimaryKey val id:String,val observationId:String,val targetKey:String,val instrumentId:String?,val valuationId:String?,val fxId:String?,val appliedAt:String) {
    init {require((valuationId==null)!=(fxId==null));require((instrumentId==null)==(valuationId==null))}
}
@Dao interface PublicDao {
    @Query("SELECT * FROM public_settings WHERE id=1") suspend fun settings():PublicSettingsEntity?
    @Query("SELECT * FROM public_observations ORDER BY provider, series, date, revision") suspend fun observations():List<PublicObservationEntity>
    @Query("SELECT * FROM nav_bindings ORDER BY instrumentId, revision") suspend fun bindings():List<NavBindingEntity>
    @Query("SELECT * FROM public_refresh_runs ORDER BY attemptedAt, id") suspend fun runs():List<PublicRunEntity>
    @Query("SELECT * FROM public_applications") suspend fun applications():List<PublicApplicationEntity>
    @Upsert suspend fun save(value:PublicSettingsEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:PublicObservationEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:NavBindingEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:PublicRunEntity)
    @Insert(onConflict=OnConflictStrategy.ABORT) suspend fun insert(value:PublicApplicationEntity)
}
internal fun PublicObservation.entity():PublicObservationEntity {val d=datum;return PublicObservationEntity(id.toString(),d.provider.name,d.kind.name,d.series,d.label,d.value.toPlainString(),d.currency?.name,d.units,d.date.toString(),d.sourceUrl,d.validation.name,d.basis,d.details,d.publication,d.identity,retrievedAt.toString(),revision,fingerprint)}
internal fun PublicObservationEntity.domain()=PublicObservation(UUID.fromString(id),PublicDatum(PublicProvider.valueOf(provider),PublicKind.valueOf(kind),series,label,value.toBigDecimal(),currency?.let(CurrencyCode::valueOf),units,LocalDate.parse(date),sourceUrl,PublicValidation.valueOf(validation),basis,details,publication,identity),Instant.parse(retrievedAt),revision,fingerprint)
internal fun NavBindingEntity.domain()=NavBinding(UUID.fromString(id),UUID.fromString(instrumentId),schemeCode,isin,active,revision,Instant.parse(recordedAt))
internal suspend fun PublicDao.readPublic()=PublicBook(settings()?.networkEnabled ?: false,observations().map {it.domain()},bindings().map {it.domain()},
    runs().map {PublicRun(UUID.fromString(it.id),PublicProvider.valueOf(it.provider),Instant.parse(it.attemptedAt),Instant.parse(it.finishedAt),it.success,it.count,it.message)},
    applications().map {PublicApplication(UUID.fromString(it.id),UUID.fromString(it.observationId),it.instrumentId?.let(UUID::fromString),it.valuationId?.let(UUID::fromString),it.fxId?.let(UUID::fromString),Instant.parse(it.appliedAt))})
