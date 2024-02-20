package dev.lutergs.santa.trade.worker.infra.impl

import dev.lutergs.santa.trade.worker.domain.Trader
import dev.lutergs.santa.trade.worker.domain.entity.WorkerTradeResult
import dev.lutergs.santa.trade.worker.infra.KafkaMessage
import dev.lutergs.santa.trade.worker.infra.KafkaProxyMessageSender
import dev.lutergs.santa.util.SellType
import dev.lutergs.santa.util.toStrWithStripTrailing
import dev.lutergs.upbitclient.api.exchange.order.OrderRequest
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.api.exchange.order.PlaceOrderRequest
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.OrderSide
import dev.lutergs.upbitclient.dto.OrderType
import dev.lutergs.upbitclient.webclient.BasicClient
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Duration
import java.util.*

class TraderImpl (
  private val kafkaProxyMessageSender: KafkaProxyMessageSender,
  private val topicName: String,
  private val client: BasicClient,
  watchIntervalSecond: Long,
): Trader {
  private val waitWatchSecond: Duration = Duration.ofSeconds(watchIntervalSecond)
  private val logger = LoggerFactory.getLogger(TraderImpl::class.java)

  // 현재 보유한 코인을 시장가로 판매
  override fun sellMarket(wtr: WorkerTradeResult, sellType: SellType): Mono<WorkerTradeResult> {
    return PlaceOrderRequest(wtr.buy.market, OrderType.MARKET, OrderSide.ASK, volume = wtr.buy.totalVolume())
      .let { req ->
        this.client.order.placeOrder(req)
          .flatMap { this.waitOrderUntilComplete(it.uuid) }
          .flatMap { Mono.fromCallable { wtr.completeSellOrder(it, sellType) } }
          .doOnNext {  }
          .flatMap {
            this.kafkaProxyMessageSender.sendPost(this.topicName, KafkaMessage(key = it.buy.uuid.toString(), value = it))
              .thenReturn(it)
              .onErrorResume { err ->
                this.logger.error("시장가 저장 Kafka 전송시 에러가 발생했습니다! dto : ${wtr}, sellType: ${sellType.name}", err)
                Mono.fromCallable { it }
              }
          }
      }
  }

  // 코인을 시장가로 구매 후 TradeResult 변환
  override fun buyMarket(market: MarketCode, money: BigDecimal): Mono<WorkerTradeResult> {
    return PlaceOrderRequest(market, OrderType.PRICE, OrderSide.BID, price = money)
      .let { req -> this.client.order.placeOrder(req)
        .flatMap { this.waitOrderUntilComplete(it.uuid) }
        .flatMap { Mono.fromCallable { WorkerTradeResult(it) } }
        .flatMap {
          this.kafkaProxyMessageSender.sendPost(this.topicName, KafkaMessage(key = it.buy.uuid.toString(), value = it))
            .thenReturn(it)
            .onErrorResume { err ->
              this.logger.error("시장가 구매 Kafka 전송시 에러가 발생했습니다! market : ${market}, money: ${money.toStrWithStripTrailing()}", err)
              Mono.fromCallable { it }
            }
        }
      }
  }

  // 지정가 매도 주문 실행
  override fun placeSellLimit(wtr: WorkerTradeResult, price: BigDecimal): Mono<WorkerTradeResult> {
    return PlaceOrderRequest(wtr.buy.market, OrderType.LIMIT, OrderSide.ASK, wtr.buy.totalVolume(), price.stripTrailingZeros())
      .let { this.client.order.placeOrder(it) }
      .flatMap { this.client.order.getOrder(OrderRequest(it.uuid)) }
      .flatMap { Mono.fromCallable { wtr.sellLimitOrderPlaced(it) } }
      .flatMap {
        this.kafkaProxyMessageSender.sendPost(this.topicName, KafkaMessage(key = it.buy.uuid.toString(), value = it))
          .thenReturn(it)
          .onErrorResume { err ->
            this.logger.error("시장가 저장 Kafka 전송시 에러가 발생했습니다! dto : ${wtr}, price: ${price.toStrWithStripTrailing()}", err)
            Mono.fromCallable { it }
          }
      }
  }

  override fun getSellLimitStatus(wtr: WorkerTradeResult): Mono<OrderResponse> {
    return if (wtr.sell != null && wtr.sell?.orderType == OrderType.LIMIT) {
      this.client.order.getOrder(OrderRequest(wtr.sell!!.uuid))
    } else {
      throw IllegalStateException("매도 주문이 없거나, 지정가 주문이 아닙니다.")
    }
  }

  // 지정가 매도 주문이 완료되었을 때 데이터 기록
  override fun finishSellLimit(wtr: WorkerTradeResult): Mono<WorkerTradeResult> {
    return this.kafkaProxyMessageSender.sendPost(this.topicName, KafkaMessage(key = wtr.buy.uuid.toString(), value = wtr))
      .thenReturn(wtr)
      .onErrorResume { err ->
        this.logger.error("지정가 매도 주문 Kafka 전송시 에러가 발생했습니다! dto : $wtr", err)
        Mono.fromCallable { wtr }
      }
  }

  // 지정가 매도 주문 취소
  override fun cancelSellLimit(wtr: WorkerTradeResult): Mono<WorkerTradeResult> {
    return if (wtr.sellType == SellType.PLACED) {
      this.client.order.cancelOrder(OrderRequest(wtr.sell!!.uuid))
        .flatMap { Mono.fromCallable { wtr.cancelSellOrder() } }
        .flatMap {
          this.kafkaProxyMessageSender.sendPost(this.topicName, KafkaMessage(key = it.buy.uuid.toString(), value = it))
            .thenReturn(it).onErrorResume { err ->
              this.logger.error("지정가 매도주문 취소 Kafka 전송시 에러가 발생했습니다! dto : $wtr", err)
              Mono.fromCallable { it }
            }
        }
    } else {
      throw IllegalStateException("매도 주문이 지정가가 아니거나, 완료된 주문입니다.")
    }
  }

  // 지정가 구매 주문
  override fun buyLimit(market: MarketCode, volume: BigDecimal, price: BigDecimal): Mono<WorkerTradeResult> {
    TODO("Not yet implemented")
  }

  private fun waitOrderUntilComplete(uuid: UUID): Mono<OrderResponse> {
    return Mono.defer { this.client.order.getOrder(OrderRequest(uuid)) }
      .flatMap {
        if (it.isFinished()) {
          Mono.just(it)
        } else {
          Mono.empty()
        }
      }.repeatWhenEmpty (Integer.MAX_VALUE) {
        it.delayElements(this.waitWatchSecond)
      }
  }
}