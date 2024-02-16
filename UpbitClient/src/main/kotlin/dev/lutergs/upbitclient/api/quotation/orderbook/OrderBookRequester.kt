package dev.lutergs.upbitclient.api.quotation.orderbook

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.MarketCodeDeserializer
import dev.lutergs.upbitclient.dto.Markets
import dev.lutergs.upbitclient.webclient.Requester
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.math.RoundingMode

class OrderBookRequester(requester: Requester) : RequestDao(requester) {

  fun getOrderBook(markets: Markets): Flux<OrderBookResponse> {
    return this.requester.getMany("/orderbook", markets, OrderBookResponse::class)
  }
}


/**
 * 호가 정보를 나타내는 데이터 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property market          마켓 코드
 * @property timestamp       호가 생성 시각
 * @property totalAskSize    호가 매도 총 잔량
 * @property totalBidSize    호가 매수 총 잔량
 * @property orderbookUnits  호가 리스트
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderBookResponse(
  @JsonProperty("market") val market: MarketCode,
  @JsonProperty("timestamp") val timestamp: Long,
  @JsonProperty("total_ask_size") val totalAskSize: BigDecimal,
  @JsonProperty("total_bid_size") val totalBidSize: BigDecimal,
  @JsonProperty("orderbook_units") val orderbookUnits: List<OrderBookUnit>
)

/**
 * 개별 호가 단위를 나타내는 데이터 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property askPrice  매도호가
 * @property bidPrice  매수호가
 * @property askSize   매도 잔량
 * @property bidSize   매수 잔량
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderBookUnit(
  @JsonProperty("ask_price") val askPrice: BigDecimal,
  @JsonProperty("bid_price") val bidPrice: BigDecimal,
  @JsonProperty("ask_size") val askSize: BigDecimal,
  @JsonProperty("bid_size") val bidSize: BigDecimal
)

/***
 * 업데이트된 호가 계산 방식 도입 https://docs.upbit.com/docs/market-info-trade-price-detail
 *
 */
object OrderStep {
  fun calculateOrderStepPrice(price: BigDecimal): BigDecimal {
    return when {
      price >= BigDecimal(2000000) -> price.setScale(-3, RoundingMode.HALF_UP)
      price >= BigDecimal(1000000) -> (price / BigDecimal(500))
        .setScale(0, RoundingMode.HALF_UP)
        .let { it * BigDecimal(500) }
      price >= BigDecimal(500000) -> price.setScale(-2, RoundingMode.HALF_UP)
      price >= BigDecimal(100000) -> (price / BigDecimal(50))
        .setScale(0, RoundingMode.HALF_UP)
        .let { it * BigDecimal(50) }
      price >= BigDecimal(10000) -> price.setScale(-1, RoundingMode.HALF_UP)
      price >= BigDecimal(1000) -> price.setScale(0, RoundingMode.HALF_UP)
      price >= BigDecimal(100) -> price.setScale(1, RoundingMode.HALF_UP)
      price >= BigDecimal(10) -> price.setScale(2, RoundingMode.HALF_UP)
      price >= BigDecimal(1) -> price.setScale(3, RoundingMode.HALF_UP)
      price >= BigDecimal(0.1) -> price.setScale(4,RoundingMode.HALF_UP)
      price >= BigDecimal(0.01) -> price.setScale(5, RoundingMode.HALF_UP)
      price >= BigDecimal(0.001) -> price.setScale(6, RoundingMode.HALF_UP)
      price >= BigDecimal(0.0001) -> price.setScale(7, RoundingMode.HALF_UP)
      else -> price.setScale(8, RoundingMode.HALF_UP)
    }.stripTrailingZeros()
  }
}
