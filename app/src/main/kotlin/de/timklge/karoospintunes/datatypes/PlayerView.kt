package de.timklge.karoospintunes.datatypes

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 150)
@Composable
fun Weather() {

    val fontSize = 14f

    Column(modifier = GlanceModifier.fillMaxHeight().padding(2.dp).width(85.dp)) {
        /* Row(modifier = GlanceModifier.defaultWeight(), horizontalAlignment = rowAlignment, verticalAlignment = Alignment.CenterVertically) {
            Image(
                modifier = GlanceModifier.defaultWeight(),
                provider = ImageProvider(getWeatherIcon(current)),
                contentDescription = "Current weather information",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = rowAlignment) {
            if (timeLabel != null){
                Text(
                    text = timeLabel,
                    style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
                )

                Spacer(modifier = GlanceModifier.width(5.dp))
            }

            Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(R.drawable.thermometer),
                contentDescription = "Temperature",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )

            Text(
                text = "${temperature}${temperatureUnit.unitDisplay}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp), textAlign = TextAlign.Center)
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalAlignment = rowAlignment) {
            /* Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(R.drawable.water_regular),
                contentDescription = "Rain",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            ) */

            val precipitationProbabilityLabel = if (precipitationProbability != null) "${precipitationProbability}%," else ""
            Text(
                text = "${precipitationProbabilityLabel}${ceil(precipitation).toInt().coerceIn(0..9)}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
            )

            Spacer(modifier = GlanceModifier.width(5.dp))

            Image(
                modifier = GlanceModifier.height(20.dp).width(12.dp),
                provider = ImageProvider(getArrowBitmapByBearing(baseBitmap, windBearing + 180)),
                contentDescription = "Current wind direction",
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(ColorProvider(Color.Black, Color.White))
            )


            Text(
                text = "$windSpeed,${windGusts}",
                style = TextStyle(color = ColorProvider(Color.Black, Color.White), fontFamily = FontFamily.Monospace, fontSize = TextUnit(fontSize, TextUnitType.Sp))
            )
        } */
    }
}