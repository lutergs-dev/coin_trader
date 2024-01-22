package dev.lutergs.upbitclient.api.quotation.ticker

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbeatclient.dto.*
import dev.lutergs.upbitclient.webclient.Requester
import dev.lutergs.upbitclient.dto.*
import reactor.core.publisher.Flux
import java.time.LocalDate
import java.time.LocalTime

class TickerRequester(requester: Requester) : RequestDao(requester) {

    fun getTicker(request: Markets): Flux<TickerResponse> {
        return this.requester.getMany("/ticker", request, TickerResponse::class)
    }
}

data class TickerResponse(
  @JsonDeserialize(using = MarketCodeDeserializer::class)
    @JsonProperty("market")                 val market: MarketCode,
  @JsonDeserialize(using = DateDeserializer::class)
    @JsonProperty("trade_date")             val tradeDate: LocalDate,
  @JsonDeserialize(using = TimeDeserializer::class)
    @JsonProperty("trade_time")             val tradeTime: LocalTime,
  @JsonDeserialize(using = DateDeserializer::class)
    @JsonProperty("trade_date_kst")         val tradeDateKst: LocalDate,
  @JsonDeserialize(using = TimeDeserializer::class)
    @JsonProperty("trade_time_kst")         val tradeTimeKst: LocalTime,
  @JsonProperty("trade_timestamp")        val tradeTimestamp: Long,
  @JsonProperty("opening_price")          val openingPrice: Double,
  @JsonProperty("high_price")             val highPrice: Double,
  @JsonProperty("low_price")              val lowPrice: Double,
  @JsonProperty("trade_price")            val tradePrice: Double,
  @JsonProperty("prev_closing_price")     val previousClosingPrice: Double,
  @JsonProperty("change")                 val change: Change,
  @JsonProperty("change_price")           val changePrice: Double,
  @JsonProperty("change_rate")            val changeRate: Double,
  @JsonProperty("signed_change_price")    val signedChangePrice: Double,
  @JsonProperty("signed_change_rate")     val signedChangeRate: Double,
  @JsonProperty("trade_volume")           val tradeVolume: Double,
  @JsonProperty("acc_trade_price")        val accTradePrice: Double,
  @JsonProperty("acc_trade_price_24h")    val accTradePrice24h: Double,
  @JsonProperty("acc_trade_volume")       val accTradeVolume: Double,
  @JsonProperty("acc_trade_volume_24h")   val accTradeVolume24h: Double,
  @JsonProperty("highest_52_week_price")  val highest52weekPrice: Double,
  @JsonDeserialize(using = DateWithHyphenDeserializer::class)
    @JsonProperty("highest_52_week_date")   val highest52weekDate: LocalDate,
  @JsonProperty("lowest_52_week_price")   val lowest52weekPrice: Double,
  @JsonDeserialize(using = DateWithHyphenDeserializer::class)
    @JsonProperty("lowest_52_week_date")    val lowest52weekDate: LocalDate,
  @JsonProperty("timestamp")              val timestamp: Long
)


enum class Change {
    EVEN, RISE, FALL
}


