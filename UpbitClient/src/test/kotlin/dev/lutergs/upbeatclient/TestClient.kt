package dev.lutergs.upbeatclient

import dev.lutergs.upbeatclient.api.exchange.order.OrderRequest
import dev.lutergs.upbeatclient.api.exchange.order.OrdersRequest
import dev.lutergs.upbeatclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbeatclient.api.quotation.ticker.TickerRequest
import dev.lutergs.upbeatclient.dto.OrderState
import dev.lutergs.upbeatclient.dto.OrderType
import dev.lutergs.upbeatclient.webclient.Client
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

class TestClient {

    private val client = Client(
    )

    @Test
    fun `전체 계좌 조회 테스트`() {
        this.client.account.getAccount()
            .collectList()
            .block()
            .let { list ->
                when {
                    list == null -> {
                        println("response is null")
                    }
                    list.isEmpty() -> {
                        println("response is empty")
                    }
                    else -> {
                        list.forEach { println(it.toString()) }
                    }
                }
            }
    }

    @Test
    fun `마켓 코드 조회 테스트`() {
        this.client.market.getMarketCode()
            .flatMap {
                println(it.toString())
                Mono.just(it)
            }.blockLast()
    }

    /**
     * 업비트 차트에서 보여지는 거래대금도 최근 24시간임
     * */
    @Test
    fun `현재가 정보 조회 테스트`() {
        this.client.market.getMarketCode()
            .filter { it.market.base == "KRW" }
            .collectList()
            .flatMap { mcr -> Mono.fromCallable { TickerRequest(mcr.map { it.market }) } }
            .flatMapMany { this.client.ticker.getTicker(it) }
            .sort { o1, o2 ->
                (o1.accTracePrice24h - o2.accTracePrice24h).roundToInt()
            }
            .flatMap {
                println(it.toString())
                Mono.just(it)
            }.blockLast()
    }

    @Test
    fun `코인 구매, 조회, 취소 테스트`() {
        this.client.market.getMarketCode()
            .filter { it.market.base == "KRW" }
            .collectList()
            .flatMap { mcr -> Mono.fromCallable { TickerRequest(mcr.map { it.market }) } }
            .flatMapMany { this.client.ticker.getTicker(it) }
            .sort { o1, o2 -> (o2.accTracePrice24h - o1.accTracePrice24h).roundToInt() }
            .flatMap {
                println(it)
                Mono.just(it)
            }
            .next()
            .flatMap {
                // TODO : 주문최소단위를 결정하는 것이 중요함. Orderbook 을 보고 호가 간을 비교하는것이 가장 적절할 것이라 생각함.
                val buyPrice = (it.tradePrice * 0.8).toInt()
                val buyVolumeKRW = 20000
                val buyVolume = (buyVolumeKRW.toDouble() / buyPrice.toDouble())
                    .let { v -> BigDecimal(v).setScale(6, RoundingMode.HALF_UP).toDouble() }
                PlaceOrderRequest(
                    market = it.market,
                    orderType = OrderType.LIMIT,
                    volume = buyVolume,
                    price = buyPrice.toDouble()
                ).let { req -> this.client.order.placeOrder(req) } }
            .flatMapMany {
                println("주문 성공 : $it")
                OrdersRequest(
                    market = it.market,
                    uuids = listOf(it.uuid),
                    state = OrderState.WAIT
                ).let { req -> this.client.order.getOrders(req) } }
            .collectList()
            .flatMap {
                println("주문들 조회 성공! ${it.joinToString(separator = "\n")}")
                OrderRequest(it.first().uuid)
                    .let { req -> this.client.order.getOrder(req) } }
            .flatMap {
                println("주문 조회 성공! $it")
                OrderRequest(it.uuid)
                    .let { req -> this.client.order.cancelOrder(req) } }
            .flatMap {
                println("주문 취소 성공! $it")
                this.client.order.getChance(it.market) }
            .flatMap {
                println("주문 가능 정보 조회 성공! $it")
                Mono.just(it) }
            .onErrorResume {
                println("error occured! error is ${it::class.java.name}")
                if (it is WebClientResponseException) {
                    println(it.responseBodyAsString)
                }
                it.printStackTrace()
                Mono.empty()
            }
            .block()
    }
}