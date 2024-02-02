package dev.lutergs.upbitclient

import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrdersRequest
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.api.quotation.candle.CandleMinuteRequest
import dev.lutergs.upbitclient.dto.*
import dev.lutergs.upbitclient.webclient.BasicClient
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.util.*
import kotlin.math.roundToInt

class TestBasicClient {

  private val basicClient = BasicClient(
    "a",
    "b"
  )

  @Test
  fun `전체 계좌 조회 테스트`() {
    this.basicClient.account.getAccount()
      .collectList()
      .block()
      .let { list ->
        when {
          list == null -> println("response is null")
          list.isEmpty() -> println("response is empty")
          else -> list.forEach { println(it.toString()) }
        }
      }
  }

  @Test
  fun `마켓 코드 조회 테스트`() {
    this.basicClient.market.getMarketCode()
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
    this.basicClient.market.getMarketCode()
      .filter { it.market.base == "KRW" }
      .collectList()
      .flatMap { mcr -> Mono.fromCallable { Markets(mcr.map { it.market }) } }
      .flatMapMany { this.basicClient.ticker.getTicker(it) }
      .sort { o1, o2 ->
        (o2.accTradePrice24h - o1.accTradePrice24h).roundToInt()
      }
      .take(25, true)
      .delayElements(Duration.ofMillis(200))
      .flatMap {
        Mono.zip(
          this.basicClient.orderBook.getOrderBook(Markets.fromMarket(it.market)).next(),
          this.basicClient.candle.getMinute(CandleMinuteRequest(it.market, 3, 60)).next()
        ).flatMap { t ->
          println(t.t1)
          println(t.t2)
          Mono.just(t)
        }
      }.flatMap {
        println(it.toString())
        Mono.just(it)
      }.blockLast()
  }

  @Test
  fun `호가 정보 조회 테스트`() {
    this.basicClient.market.getMarketCode()
      .filter { it.market.base == "KRW" }
      .flatMap { Mono.just(it.market) }
      .collectList()
      .flatMapMany { this.basicClient.orderBook.getOrderBook(Markets(it)) }
      .flatMap {
        println(it.toString())
        Mono.just(it)
      }.onErrorResume {
        println("error occured! error is ${it::class.java.name}")
        if (it is WebClientResponseException) {
          println(it.responseBodyAsString)
        }
        it.printStackTrace()
        Mono.empty()
      }.blockLast()
  }

  @Test
  fun `캔들 차트 조회 테스트`() {
    this.basicClient.market.getMarketCode()
      .filter { it.market.base == "KRW" }
      .next()
      .flatMapMany { this.basicClient.candle.getMinute(CandleMinuteRequest(it.market, 100, 5)) }
      .flatMap {
        println(it.toString())
        Mono.just(it)
      }.onErrorResume {
        println("error occured! error is ${it::class.java.name}")
        if (it is WebClientResponseException) {
          println(it.responseBodyAsString)
        }
        it.printStackTrace()
        Mono.empty()
      }.blockLast()
  }

  @Test
  fun `코인 구매, 조회, 취소 테스트`() {
    this.basicClient.market.getMarketCode()
      .filter { it.market.base == "KRW" }
      .collectList()
      .flatMap { mcr -> Mono.fromCallable { Markets(mcr.map { it.market }) } }
      .flatMapMany { this.basicClient.ticker.getTicker(it) }
      .sort { o1, o2 -> (o2.accTradePrice24h - o1.accTradePrice24h).roundToInt() }
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
          type = OrderType.LIMIT,
          side = OrderSide.BID,
          volume = buyVolume,
          price = buyPrice.toDouble(),
        ).let { req -> this.basicClient.order.placeOrder(req) }
      }
      .flatMapMany {
        println("주문 성공 : $it")
        OrdersRequest(
          market = it.market,
          uuids = listOf(it.uuid),
          state = OrderState.WAIT
        ).let { req -> this.basicClient.order.getOrders(req) }
      }
      .collectList()
      .flatMap {
        println("주문들 조회 성공! ${it.joinToString(separator = "\n")}")
        OrderRequest(it.first().uuid)
          .let { req -> this.basicClient.order.getOrder(req) }
      }
      .flatMap {
        println("주문 조회 성공! $it")
        OrderRequest(it.uuid)
          .let { req -> this.basicClient.order.cancelOrder(req) }
      }
      .flatMap {
        println("주문 취소 성공! $it")
        this.basicClient.order.getChance(it.market)
      }
      .flatMap {
        println("주문 가능 정보 조회 성공! $it")
        Mono.just(it)
      }
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

  @Test
  fun `시장가 매수 매도 DTO 출력 테스트`() {
    PlaceOrderRequest(MarketCode("KRW", "BTC"), OrderType.PRICE, OrderSide.BID, price = 10000.0)
      .let { this.basicClient.order.testPlaceOrder(it) }
      .flatMap {
        println("first price buy order result : $it")
        val uuid = JSONObject(it).getString("uuid")
        Mono.just(uuid)
      }.flatMap { uuid ->
        Mono.defer { this.basicClient.order.testGetOrder(OrderRequest(UUID.fromString(uuid))) }
          .delayElement(Duration.ofMillis(80))
          .repeat(10)
          .flatMap {
            println("[Price] Order result is: $it")
            Mono.just(it)
          }.collectList()
      }.flatMap { list -> Mono.just(list.first()) }
      .flatMap { order ->
        val uuid = JSONObject(order).getString("uuid")
        this.basicClient.order.getOrder(OrderRequest(UUID.fromString(uuid)))
          .doOnNext {
            println("real order is $it")
            println("additional : ${it.isFinished()}, ${it.totalVolume()}, ${it.avgPrice()}")
          }
      }.flatMap {
        PlaceOrderRequest(it.market, OrderType.MARKET, OrderSide.ASK, volume = it.totalVolume())
          .let { p -> this.basicClient.order.testPlaceOrder(p) }
      }.flatMap {
        println("first market sell order result : $it")
        val uuid = JSONObject(it).getString("uuid")
        Mono.just(uuid)
      }.flatMap { uuid ->
        Mono.defer { this.basicClient.order.testGetOrder(OrderRequest(UUID.fromString(uuid))) }
          .delayElement(Duration.ofMillis(80))
          .repeat(10)
          .flatMap {
            println("[Market] Order result is: $it")
            Mono.just(it)
          }.collectList()
      }.flatMap {
        val uuid = it.first().let { o -> JSONObject(o).getString("uuid") }
        this.basicClient.order.getOrder(OrderRequest(UUID.fromString(uuid)))
          .doOnNext {
            println("real order is $it")
            println("additional : ${it.isFinished()}, ${it.totalVolume()}, ${it.avgPrice()}")
          }
      }.block()
  }
}