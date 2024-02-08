package dev.lutergs.santa.universal.influx

import com.influxdb.v3.client.Point
import com.influxdb.v3.client.PointValues
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime
import java.util.*

fun BigDecimal.roundToDecimalString(place: Int): String {
  return this
    .setScale(place, RoundingMode.HALF_UP)
    .toString()
}

data class CoinTradeSnapshot(
  val coin: String,
  val currentPrice: BigDecimal,
  val phase: Long,
  val upperLimit: BigDecimal,
  val lowerLimit: BigDecimal,
  val uuid: UUID,
  val timestamp: OffsetDateTime
) {
  fun toPoint() = Point
    .measurement(measurement)
    .setTags(mutableMapOf(
      "coin" to this.coin
    )).setFields(mutableMapOf<String, Any>(
      "currentPrice" to this.currentPrice.roundToDecimalString(4),
      "phase" to this.phase,
      "upperLimit" to this.currentPrice.roundToDecimalString(4),
      "lowerLimit" to this.lowerLimit.roundToDecimalString(4),
      "uuid" to this.uuid.toString()
    )).setTimestamp(this.timestamp.toInstant())

  companion object {
    private val measurement = "coin-trade-status"
//    fun fromPointValues(p: PointValues) {
//      CoinTradeSnapshot(
//        coin = p.getTag("coin")!!,
//        currentPrice = (p.getField("currentPrice") as String).toDouble(),
//
//      )
//    }
  }
}