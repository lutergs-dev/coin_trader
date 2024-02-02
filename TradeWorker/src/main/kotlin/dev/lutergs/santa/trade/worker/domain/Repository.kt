package dev.lutergs.santa.trade.worker.domain

import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.SellType
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import dev.lutergs.santa.trade.worker.infra.KafkaMessageResponse
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import reactor.core.publisher.Mono
import java.util.UUID

interface LogRepository {

  fun newBuyOrder(response: OrderResponse): Mono<OrderResponse>
  fun finishBuyOrder(response: OrderResponse): Mono<OrderResponse>
  fun completeBuyOrder(response: OrderResponse): Mono<OrderResponse>
  fun placeSellOrder(response: OrderResponse, buyUuid: UUID): Mono<OrderResponse>
  fun finishSellOrder(buyResponse: OrderResponse, sellResponse: OrderResponse, sellType: SellType): Mono<OrderResponse>
  fun completeSellOrder(buyResponse: OrderResponse, sellResponse: OrderResponse, sellType: SellType): Mono<OrderResponse>
  fun cancelSellOrder(sellUuid: UUID, buyUuid: UUID): Mono<Void>
}

interface MessageSender {
  fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse>

  fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse>
}