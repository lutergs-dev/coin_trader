package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.MarketCode
import java.util.UUID


data class AlarmMessage (
  val key: AlarmMessageKey,
  val value: AlarmMessageValue
)

data class AlarmMessageKey (
  val coinName: String
)

data class AlarmMessageValue (
  val uuid: UUID,
)


data class TradeResult (
  val key: UUID,
  val value: TradeResultValue
)

data class TradeResultValue (
  val profit: Double,
  val buy: OrderResponse,
  val sell: OrderResponse
)

data class MainTrade(
  val market: MarketCode,
  val money: Int
)