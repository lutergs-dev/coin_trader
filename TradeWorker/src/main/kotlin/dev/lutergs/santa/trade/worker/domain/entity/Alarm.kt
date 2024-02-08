package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import java.math.BigDecimal
import java.util.*


data class DangerCoinMessage(
  val key: DangerCoinMsgKey,
  val value: DangerCoinMsgValue
) {
  companion object {
    fun fromOrderResponse(order: OrderResponse): DangerCoinMessage {
      return DangerCoinMessage(DangerCoinMsgKey(order.market.quote), DangerCoinMsgValue(order.uuid))
    }
  }
}

data class DangerCoinMsgKey(
  val coinName: String
)

data class DangerCoinMsgValue(
  val uuid: UUID
)


data class TradeResultMessage(
  val key: TradeResultMsgKey,
  val value: TradeResultMsgValue
)

data class TradeResultMsgKey(
  val uuid: UUID,
  val profit: BigDecimal
)

data class TradeResultMsgValue(
  val buy: OrderResponse,
  val sell: OrderResponse
)