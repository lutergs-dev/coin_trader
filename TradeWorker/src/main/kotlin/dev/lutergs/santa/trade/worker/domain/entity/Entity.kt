package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.MarketCode
import java.util.UUID


data class AlarmMessage (
  val key: AlarmMessageKey,
  val value: AlarmMessageValue
) {
  companion object {
    fun fromOrderResponse(order: OrderResponse): AlarmMessage {
      return AlarmMessage(AlarmMessageKey(order.market.quote), AlarmMessageValue(order.uuid))
    }
  }
}

data class AlarmMessageKey (
  val coinName: String
)

data class AlarmMessageValue (
  val uuid: UUID,
)


data class TradeResult (
  val key: UUID,
  val value: TradeResultValue
) {
  companion object {
    fun fromTradeStatus(tradeStatus: TradeStatus): TradeResult {
      return if (tradeStatus.sell == null) {
        throw IllegalStateException("판매 정보가 없습니다!")
      } else {
        TradeResult(
          key = tradeStatus.sell.order.uuid,
          value = TradeResultValue(
            profit = tradeStatus.getEarnPrice(),
            buy = tradeStatus.buy.order,
            sell = tradeStatus.sell.order
          )
        )
      }
    }
  }
}

data class TradeResultValue (
  val profit: Double,
  val buy: OrderResponse,
  val sell: OrderResponse
)

data class MainTrade(
  val market: MarketCode,
  val money: Int
)

enum class SellType {
  PROFIT, LOSS, TIMEOUT, STOP_LOSS, STOP_PROFIT
}

data class Phase(
  val phase1: Phase1,
  val phase2: Phase2
) {
  fun totalWaitMinute(): Long {
    return phase1.waitMinute + phase2.waitMinute
  }
}

data class Phase1(
  val waitMinute: Long,
  val profitPercent: Double,
  val lossPercent: Double
) {
  fun getProfitPrice(price: Double): Double {
    return price * (1.0 + (profitPercent * 0.01))
  }

  fun getLossPrice(price: Double): Double {
    return price * (1.0 - (lossPercent * 0.01))
  }
}

data class Phase2(
  val waitMinute: Long,
  val lossPercent: Double
) {
  fun getLossPrice(price: Double): Double {
    return price * (1.0 - (lossPercent * 0.01))
  }
}


data class TradeStatus(
  val buy: Order,
  val sell: Order?,
  val sellType: SellType?
) {
  fun sellFinished(response: OrderResponse, sellType: SellType): TradeStatus {
    return TradeStatus(
      buy = this.buy,
      sell = Order(response, status = OrderStatus.COMPLETE),
      sellType = sellType
    )
  }

  fun sellPlaced(response: OrderResponse, sellType: SellType): TradeStatus {
    return TradeStatus(
      buy = this.buy,
      sell = Order(response, status = OrderStatus.WAIT_FOR_COMPLETE),
      sellType = sellType
    )
  }

  fun sellTimeout(response: OrderResponse, sellType: SellType): TradeStatus {
    return TradeStatus(
      buy = this.buy,
      sell = Order(response, status = OrderStatus.TIMEOUT),
      sellType = sellType
    )
  }

  fun getEarnPrice(): Double {
    return if (sell != null) {
      (this.sell.order.price * this.sell.order.volume) - (this.buy.order.price * this.buy.order.volume) - ((listOf(this.sell.order.paidFee, this.sell.order.reservedFee, this.sell.order.remainingFee).maxOrNull() ?: 0.0) + this.buy.order.paidFee)
    } else {
      0.0
    }
  }
}

data class Order(
  val order: OrderResponse,
  val status: OrderStatus
)

enum class OrderStatus {
  WAIT_FOR_COMPLETE, COMPLETE, TIMEOUT
}