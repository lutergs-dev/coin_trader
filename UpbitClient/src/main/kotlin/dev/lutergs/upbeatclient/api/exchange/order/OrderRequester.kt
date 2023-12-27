package dev.lutergs.upbeatclient.api.exchange.order

import dev.lutergs.upbeatclient.api.Param
import dev.lutergs.upbeatclient.api.RequestDao
import dev.lutergs.upbeatclient.dto.*
import dev.lutergs.upbeatclient.webclient.Requester
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

class OrderRequester(requester: Requester) : RequestDao(requester) {

    fun getChance(marketCode: MarketCode): Mono<OrderChanceResponse> {
        return this.requester.getSingle("/orders/chance", marketCode, OrderChanceResponse::class)
    }

    fun getOrder(request: OrderRequest): Mono<OrderResponse> {
        return this.requester.getSingle("/order", request, OrderResponse::class)
    }

    fun getOrders(request: OrdersRequest): Flux<OrdersResponse> {
        return this.requester.getMany("/orders", request, OrdersResponse::class)
    }

    fun cancelOrder(request: OrderRequest): Mono<CancelOrderResponse> {
        return this.requester.deleteSingle("/order", request, CancelOrderResponse::class)
    }

    fun placeOrder(request: PlaceOrderRequest): Mono<PlaceOrderResponse> {
        return this.requester.postSingle("/orders", request, PlaceOrderResponse::class)
    }
}


data class OrderRequest(
    val uuid: UUID
): Param {
    override fun toParameterString(): String {
        return this.toJwtTokenString()
    }

    override fun toJwtTokenString(): String {
        return "uuid=$uuid"
    }
}

data class OrdersRequest(
    val market: MarketCode,
    val uuids: List<UUID>,
    val state: OrderState = OrderState.WAIT,
    val page: Int = 1,
    val limit: Int = 100,
    val orderBy: Ordering = Ordering.ASC
): Param {
    override fun toParameterString(): String {
        return this.toJwtTokenString()
    }

    override fun toJwtTokenString(): String {
        val uuidStr = uuids.joinToString(separator = "&") { "uuids[]=$it" }
        return "${market.toParameterString()}&$uuidStr&${state.toJwtTokenString()}&page=$page&limit=$limit&order_by=${orderBy.name.lowercase()}"
    }
}


data class PlaceOrderRequest(
    val market: MarketCode,
    val orderType: OrderType,
    val volume: Double? = null,
    val price: Double? = null
): Param {

    init {
        when (this.orderType) {
            OrderType.LIMIT -> {
                if (!(volume != null && price != null)) {
                    throw IllegalArgumentException("지정가 매수에서 주문량과 주문가격을 지정하지 않았습니다.")
                }
            }
            OrderType.PRICE -> {
                if (!(volume == null && price != null)) {
                    throw IllegalArgumentException("시장가 주문(매수) 에서 주문량을 지정했거나, 주문 가격을 지정하지 않았습니다.")
                }
            }
            OrderType.MARKET -> {
                if (!(volume != null && price == null)){
                    throw IllegalArgumentException("시장가 주문(매도) 에서 주문량을 지정하지 않았거나, 주문 가격을 지정했습니다.")
                }
            }
        }
    }
    override fun toParameterString(): String {
        return when (this.orderType) {
            OrderType.LIMIT -> "${market.toParameterString()}&side=bid&volume=$volume&price=$price&ord_type=${orderType.name.lowercase()}"
            OrderType.PRICE -> "${market.toParameterString()}&side=bid&price=$price&ord_type=${orderType.name.lowercase()}"
            OrderType.MARKET -> "${market.toParameterString()}&side=ask&volume=$volume&ord_type=${orderType.name.lowercase()}"
        }
    }

    override fun toJwtTokenString(): String {
        return when (this.orderType) {
            OrderType.LIMIT -> "${market.toJwtTokenString()}&side=bid&volume=$volume&price=$price&ord_type=${orderType.name.lowercase()}"
            OrderType.PRICE -> "${market.toJwtTokenString()}&side=bid&price=$price&ord_type=${orderType.name.lowercase()}"
            OrderType.MARKET -> "${market.toJwtTokenString()}&side=ask&volume=$volume&ord_type=${orderType.name.lowercase()}"
        }
    }
}

