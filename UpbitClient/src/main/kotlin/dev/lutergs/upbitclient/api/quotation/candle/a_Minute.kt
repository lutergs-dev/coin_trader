package dev.lutergs.upbitclient.api.quotation.candle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

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
  @JsonProperty("opening_price") val openingPrice: BigDecimal,
  @JsonProperty("high_price") val highPrice: BigDecimal,
  @JsonProperty("low_price") val lowPrice: BigDecimal,
  @JsonProperty("trade_price") val tradePrice: BigDecimal,
  @JsonProperty("timestamp") val timestamp: Long,   // TODO : OffsetDateTime 으로 변환
  @JsonProperty("candle_acc_trade_price") val candleAccTradePrice: BigDecimal,
  @JsonProperty("candle_acc_trade_volume") val candleAccTradeVolume: BigDecimal,
  @JsonProperty("unit") val unit: Int
) {
  companion object {
    fun rsi(candles: List<CandleMinuteResponse>, period: Int = candles.size / 2): BigDecimal {
      return candles
        .sortedByDescending { it.timestamp }
        .map { it.tradePrice }
        .windowed(2, 1, false) { it[1] - it[0] }
        .let { changes ->
          val sma = changes.subList(0, period)
            .let { smaPeriod -> Pair(
              smaPeriod.filter { it > BigDecimal.ZERO }.reduce{ a, b -> a + b} / BigDecimal(smaPeriod.size),
              (smaPeriod.filter { it < BigDecimal.ZERO }.reduce{ a, b -> a + b} / BigDecimal(smaPeriod.size)).negate()
            ) }
          val smoothing = BigDecimal(2.0) / (BigDecimal(period + 1))
          changes.subList(period, changes.size)
            .fold(sma) { ema, change ->
              when {
                change > BigDecimal.ZERO -> Pair(
                  (change * smoothing) + (ema.first * (BigDecimal(1.0) - smoothing)), ema.second * (BigDecimal(1.0) - smoothing))
                change < BigDecimal.ZERO -> Pair(
                  ema.first * (BigDecimal(1.0) - smoothing), ((change * BigDecimal(-1.0)) * smoothing) + (ema.second * (BigDecimal(1.0) - smoothing)))
                else -> Pair(ema.first * (BigDecimal(1.0) - smoothing), ema.second * (BigDecimal(1.0) - smoothing))
              }
            }.let {
              (if (it.second == BigDecimal.ZERO) { BigDecimal.ZERO } else { it.first / it.second })
                .let { rs -> BigDecimal(100.0) - (BigDecimal(100.0) / (BigDecimal(1.0) + rs))}
            }
        }
    }
  }
}