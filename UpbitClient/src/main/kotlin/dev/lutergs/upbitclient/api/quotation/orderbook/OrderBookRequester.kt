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
import kotlin.math.absoluteValue
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

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
  @JsonDeserialize(using = MarketCodeDeserializer::class)
  @JsonProperty("market")           val market: MarketCode,
  @JsonProperty("timestamp")        val timestamp: Long,
  @JsonProperty("total_ask_size")   val totalAskSize: Double,
  @JsonProperty("total_bid_size")   val totalBidSize: Double,
  @JsonProperty("orderbook_units")  val orderbookUnits: List<OrderBookUnit>
) {
  private val step = orderbookUnits.windowed(2, 1, false) {
    min((it[0].bidPrice - it[1].bidPrice).absoluteValue, (it[0].askPrice - it[1].askPrice).absoluteValue)
  }.minOrNull()
    ?: throw IllegalStateException("호가 리스트가 존재하지 않습니다!")

  fun nearestStepPrice(price: Double): Double {
    val stepString = this.step.toString()
    return if (stepString.split(".")[1].toInt() == 0) {
      val stepLength = this.step.toString().substringAfter(".").length
      val origin = (price * this.step) / this.step
      (origin * (10.0.pow(stepLength))).roundToInt().toDouble() / (10.0.pow(stepLength))
    } else {
      (price * this.step).roundToInt().toDouble() / this.step
    }
  }
}

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
    @JsonProperty("ask_price")   val askPrice: Double,
    @JsonProperty("bid_price")   val bidPrice: Double,
    @JsonProperty("ask_size")    val askSize: Double,
    @JsonProperty("bid_size")    val bidSize: Double
)
