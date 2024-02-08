package dev.lutergs.upbitclient.api.quotation.ticker

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.webclient.Requester
import dev.lutergs.upbitclient.dto.*
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

class TickerRequester(requester: Requester) : RequestDao(requester) {

  fun getTicker(request: Markets): Flux<TickerResponse> {
    return this.requester.getMany("/ticker", request, TickerResponse::class)
  }
}

data class TickerResponse(
  @JsonDeserialize(using = MarketCodeDeserializer::class)
  @JsonProperty("market") val market: MarketCode,
  @JsonDeserialize(using = DateDeserializer::class)
  @JsonProperty("trade_date") val tradeDate: LocalDate,
  @JsonDeserialize(using = TimeDeserializer::class)
  @JsonProperty("trade_time") val tradeTime: LocalTime,
  @JsonDeserialize(using = DateDeserializer::class)
  @JsonProperty("trade_date_kst") val tradeDateKst: LocalDate,
  @JsonDeserialize(using = TimeDeserializer::class)
  @JsonProperty("trade_time_kst") val tradeTimeKst: LocalTime,
  @JsonProperty("trade_timestamp") val tradeTimestamp: Long,
  @JsonProperty("opening_price") val openingPrice: BigDecimal,
  @JsonProperty("high_price") val highPrice: BigDecimal,
  @JsonProperty("low_price") val lowPrice: BigDecimal,
  @JsonProperty("trade_price") val tradePrice: BigDecimal,
  @JsonProperty("prev_closing_price") val previousClosingPrice: BigDecimal,
  @JsonProperty("change") val change: Change,
  @JsonProperty("change_price") val changePrice: BigDecimal,
  @JsonProperty("change_rate") val changeRate: BigDecimal,
  @JsonProperty("signed_change_price") val signedChangePrice: BigDecimal,
  @JsonProperty("signed_change_rate") val signedChangeRate: BigDecimal,
  @JsonProperty("trade_volume") val tradeVolume: BigDecimal,
  @JsonProperty("acc_trade_price") val accTradePrice: BigDecimal,
  @JsonProperty("acc_trade_price_24h") val accTradePrice24h: BigDecimal,
  @JsonProperty("acc_trade_volume") val accTradeVolume: BigDecimal,
  @JsonProperty("acc_trade_volume_24h") val accTradeVolume24h: BigDecimal,
  @JsonProperty("highest_52_week_price") val highest52weekPrice: BigDecimal,
  @JsonDeserialize(using = DateWithHyphenDeserializer::class)
  @JsonProperty("highest_52_week_date") val highest52weekDate: LocalDate,
  @JsonProperty("lowest_52_week_price") val lowest52weekPrice: BigDecimal,
  @JsonDeserialize(using = DateWithHyphenDeserializer::class)
  @JsonProperty("lowest_52_week_date") val lowest52weekDate: LocalDate,
  @JsonProperty("timestamp") val timestamp: Long
)


enum class Change {
  EVEN, RISE, FALL
}


