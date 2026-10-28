package com.wealthos.publicdata

import androidx.test.platform.app.InstrumentationRegistry
import com.wealthos.finance.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in integration probe; ordinary regression tests never depend on Internet availability. */
@RunWith(Parameterized::class)
class LivePublicFeedsTest(private val provider:PublicProvider) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="Live {0}")
        fun parameters():List<Array<PublicProvider>> = if(InstrumentationRegistry.getArguments().getString("livePublic")=="true")listOf(PublicProvider.ECB,PublicProvider.MOSPI,PublicProvider.BIS).map {arrayOf(it)} else emptyList()
    }
    @Test fun officialHttpsFeedValidates(){val batch=PublicFeeds(HttpsPublicTransport {true}).fetch(provider);assertTrue(batch.rows.isNotEmpty());assertTrue(batch.rows.all {it.provider==provider && it.validation==PublicValidation.HTTPS_VALIDATED});println("LIVE_PUBLIC ${provider.name}: ${batch.rows.size} rows, latest ${batch.rows.maxOf {it.date}}, retrieved ${batch.retrievedAt}")}
}
