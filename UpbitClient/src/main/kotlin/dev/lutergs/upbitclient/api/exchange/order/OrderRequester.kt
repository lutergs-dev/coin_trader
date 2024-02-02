package dev.lutergs.upbitclient.api.exchange.order

import dev.lutergs.upbitclient.api.Param
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.webclient.Requester
import dev.lutergs.upbitclient.dto.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.*

class OrderRequester(requester: Requester) : RequestDao(requester) {

  fun getChance(marketCode: MarketCode): Mono<OrderChanceResponse> {
    return this.requester.getSingle("/orders/chance", marketCode, OrderChanceResponse::class)
  }

  fun getOrder(request: OrderRequest): Mono<OrderResponse> {
    return this.requester.getSingle("/order", request, OrderResponse::class)
  }

  fun getOrders(request: OrdersRequest?): Flux<OrdersResponse> {
    return this.requester.getMany("/orders", request, OrdersResponse::class)
  }

  fun cancelOrder(request: OrderRequest): Mono<CancelOrderResponse> {
    return this.requester.deleteSingle("/order", request, CancelOrderResponse::class)
  }

  fun placeOrder(request: PlaceOrderRequest): Mono<PlaceOrderResponse> {
    return this.requester.postSingle("/orders", request, PlaceOrderResponse::class)
  }

  @Deprecated("테스트 용 함수이므로, 사용 금지", ReplaceWith("this.getOrder(request)"))
  fun testGetOrder(request: OrderRequest): Mono<String> {
    return this.requester.getSingleTest("/order", request)
  }

  @Deprecated("테스트 용 함수이므로, 사용 금지", ReplaceWith("this.placeOrder(request)"))
  fun testPlaceOrder(request: PlaceOrderRequest): Mono<String> {
    return this.requester.postTest("/orders", request)
  }
}


data class OrderRequest(
  val uuid: UUID
) : Param {
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
) : Param {
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
  val type: OrderType,
  val side: OrderSide,
  val volume: Double? = null,
  val price: Double? = null,
) : Param {

  init {
    when (this.type) {
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
        if (!(volume != null && price == null)) {
          throw IllegalArgumentException("시장가 주문(매도) 에서 주문량을 지정하지 않았거나, 주문 가격을 지정했습니다.")
        }
      }
    }
  }

  override fun toParameterString(): String {
    return when (this.type) {
      OrderType.LIMIT -> "${market.toParameterString()}&side=${this.side.name.lowercase()}&volume=$volume&price=$price&ord_type=${type.name.lowercase()}"
      OrderType.PRICE -> "${market.toParameterString()}&side=${this.side.name.lowercase()}&price=$price&ord_type=${type.name.lowercase()}"
      OrderType.MARKET -> "${market.toParameterString()}&side=ask&volume=$volume&ord_type=${type.name.lowercase()}"
    }
  }

  override fun toJwtTokenString(): String {
    return when (this.type) {
      OrderType.LIMIT -> "${market.toJwtTokenString()}&side=${this.side.name.lowercase()}&volume=$volume&price=$price&ord_type=${type.name.lowercase()}"
      OrderType.PRICE -> "${market.toJwtTokenString()}&side=${this.side.name.lowercase()}&price=$price&ord_type=${type.name.lowercase()}"
      OrderType.MARKET -> "${market.toJwtTokenString()}&side=ask&volume=$volume&ord_type=${type.name.lowercase()}"
    }
  }
}

