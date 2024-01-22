package dev.lutergs.upbitclient.api.quotation.candle

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

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
    @JsonProperty("market")                   val market: String,
    @JsonProperty("candle_date_time_utc")     val candleDateTimeUtc: String,
    @JsonProperty("candle_date_time_kst")     val candleDateTimeKst: String,
    @JsonProperty("opening_price")            val openingPrice: Double,
    @JsonProperty("high_price")               val highPrice: Double,
    @JsonProperty("low_price")                val lowPrice: Double,
    @JsonProperty("trade_price")              val tradePrice: Double,
    @JsonProperty("timestamp")                val timestamp: Long,
    @JsonProperty("candle_acc_trade_price")   val candleAccTradePrice: Double,
    @JsonProperty("candle_acc_trade_volume")  val candleAccTradeVolume: Double,
    @JsonProperty("unit")                     val unit: Int
)