package dev.lutergs.upbeatclient.api.quotation.orderbook

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbeatclient.api.RequestDao
import dev.lutergs.upbeatclient.dto.MarketCode
import dev.lutergs.upbeatclient.dto.MarketCodeDeserializer
import dev.lutergs.upbeatclient.dto.Markets
import dev.lutergs.upbeatclient.webclient.Requester
import reactor.core.publisher.Flux

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
    @JsonProperty("ask_price")   val askPrice: Double,
    @JsonProperty("bid_price")   val bidPrice: Double,
    @JsonProperty("ask_size")    val askSize: Double,
    @JsonProperty("bid_size")    val bidSize: Double
)
