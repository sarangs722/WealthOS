package com.wealthos.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.wealthos.finance.ForecastPoint
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/** Synthetic, isolated Compose content; never loads or edits a financial vault. */
class ForecastAccessibilityTest {
    @get:Rule val rule=createComposeRule()
    private val points=(0..360).map {month->
        val nominal=BigDecimal(month)*BigDecimal("100")
        ForecastPoint(month,LocalDate.of(2026,1,1).plusMonths(month.toLong()),nominal,BigDecimal.ZERO,
            nominal,BigDecimal(month)*BigDecimal("60"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO)
    }
    private fun verify(dark:Boolean,fontScale:Float){
        lateinit var palette:ForecastChartPalette
        lateinit var backgrounds:List<Color>
        rule.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,fontScale)) {
                WealthTheme(darkTheme=dark) {
                    val colors=MaterialTheme.colorScheme
                    val current=forecastChartPalette()
                    SideEffect {palette=current;backgrounds=listOf(colors.surface,colors.surfaceContainerLow,colors.surfaceContainer)}
                    Surface(color=colors.surfaceContainerLow,modifier=Modifier.fillMaxWidth().testTag("fictional-chart-example")) {
                        Column {ForecastChart(points,false)}
                    }
                }
            }
        }
        rule.runOnIdle {
            // WCAG non-text graphical objects require at least 3:1 against adjacent backgrounds.
            for(series in listOf(palette.nominal,palette.real))for(background in backgrounds){
                val a=series.luminance().toDouble();val b=background.luminance().toDouble()
                val contrast=(maxOf(a,b)+0.05)/(minOf(a,b)+0.05)
                assertTrue("Chart series contrast $contrast must be >= 3:1 (dark=$dark)",contrast>=3.0)
            }
        }
        rule.onNodeWithText("Nominal: solid line · Today's purchasing power: dashed line").assertIsDisplayed()
        rule.onNodeWithContentDescription("Estimated nominal wealth (solid line) and today's purchasing power (dashed line) across 360 monthly steps. Exact dates and values follow below.").assertIsDisplayed()
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        File(context.filesDir,if(dark)"fictional-chart-dark-200.png" else "fictional-chart-light.png").outputStream().use {
            rule.onNodeWithTag("fictional-chart-example").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)
        }
    }
    @Test fun lightChartMeetsGraphicalContrastAndExplainsLineStyles(){verify(false,1f)}
    @Test fun darkChartMeetsGraphicalContrastAtDoubleFontSize(){verify(true,2f)}
    @Test fun obscuredChartExposesNeitherSeriesNorPlot(){
        rule.setContent {WealthTheme(darkTheme=true){ForecastChart(points,true)}}
        rule.onNodeWithText("Forecast chart hidden").assertIsDisplayed()
        rule.onNodeWithTag("forecast-chart").assertDoesNotExist()
        rule.onAllNodes(hasContentDescription("Estimated nominal wealth",substring=true)).assertCountEquals(0)
        rule.onAllNodes(hasText("Nominal:",substring=true)).assertCountEquals(0)
    }
}
