package dev.lutergs.upbitclient.api.quotation.candle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import reactor.core.publisher.Flux

/**
 * 시장 캔들 정보를 나타내는 데이터 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property market                마켓명
 * @property candleDateTimeUtc     캔들 기준 시각(UTC 기준)
 * @property candleDateTimeKst     캔들 기준 시각(KST 기준)
 * @property openingPrice          시가
 * @property highPrice             고가
 * @property lowPrice              저가
 * @property tradePrice            종가
 * @property timestamp             해당 캔들에서 마지막 틱이 저장된 시각
 * @property candleAccTradePrice   누적 거래 금액
 * @property candleAccTradeVolume  누적 거래량
 * @property unit                  분 단위(유닛)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class CandleMinuteResponse(
  @JsonProperty("market") val market: String,
  @JsonProperty("candle_date_time_utc") val candleDateTimeUtc: String,
  @JsonProperty("candle_date_time_kst") val candleDateTimeKst: String,
  @JsonProperty("opening_price") val openingPrice: Double,
  @JsonProperty("high_price") val highPrice: Double,
  @JsonProperty("low_price") val lowPrice: Double,
  @JsonProperty("trade_price") val tradePrice: Double,
  @JsonProperty("timestamp") val timestamp: Long,
  @JsonProperty("candle_acc_trade_price") val candleAccTradePrice: Double,
  @JsonProperty("candle_acc_trade_volume") val candleAccTradeVolume: Double,
  @JsonProperty("unit") val unit: Int
) {
  companion object {
    fun rsi(candles: List<CandleMinuteResponse>, period: Int = candles.size / 2): Double {
      return candles
        .sortedByDescending { it.timestamp }
        .map { it.tradePrice }
        .windowed(2, 1, false) { it[1] - it[0] }
        .let { changes ->
          val sma = changes.subList(0, period)
            .let { smaPeriod -> Pair(
              smaPeriod.filter { it > 0 }.sum() / smaPeriod.size,
              smaPeriod.filter { it < 0 }.sum() / smaPeriod.size * -1.0
            ) }
          val smoothing = 2.0 / (period + 1).toDouble()
          changes.subList(period, changes.size)
            .fold(sma) { ema, change ->
              when {
                change > 0 -> Pair(
                  (change * smoothing) + (ema.first * (1.0 - smoothing)), ema.second * (1 - smoothing))
                change < 0 -> Pair(
                  ema.first * (1 - smoothing), ((change * -1.0) * smoothing) + (ema.second * (1.0 - smoothing)))
                else -> Pair(ema.first * (1 - smoothing), ema.second * (1 - smoothing))
              }
            }.let {
              (if (it.second == 0.0) { 0.0 } else { it.first / it.second })
                .let { rs -> 100.0 - (100.0 / (1 + rs))}
            }
        }
    }
  }
}