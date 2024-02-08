package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.upbitclient.api.exchange.order.OrderResponse
import dev.lutergs.upbitclient.dto.OrderType
import java.math.BigDecimal

class TradeResult {
  val buy: OrderResponse
  val sell: OrderResponse?
  val sellType: SellType

  private constructor(buy: OrderResponse) {
    this.buy = buy
    this.sell = null
    this.sellType = SellType.NULL
    takeIf { this.buy.isFinished() } ?: throw IllegalStateException("매수 주문이 종료되지 않았습니다. 전체 값: $this")
  }
  private constructor(buy: OrderResponse, sell: OrderResponse, sellType: SellType) {
    this.buy = buy
    this.sell = sell
    this.sellType = sellType
  }

  fun completeSellOrder(sell: OrderResponse, sellType: SellType): TradeResult {
    if (!sell.isFinished()) throw IllegalStateException("종료되지 않은 매도 주문으로 TradeResult 의 매도주문 기록을 요청했습니다.")
    return TradeResult(this.buy, sell, sellType)
  }

  fun sellLimitOrderPlaced(sell: OrderResponse): TradeResult {
    if (sell.orderType == OrderType.LIMIT && !sell.isFinished()) {
      return TradeResult(this.buy, sell, SellType.PLACED)
    } else {
      throw IllegalStateException("시장가 주문이거나, 종료된 지정가 매도 주문 기록을 진행중인 매도 주문 기록 함수에 요청했습니다.")
    }
  }

  fun cancelSellOrder(): TradeResult {
    if (this.sellType == SellType.PLACED) {
      return TradeResult(this.buy)
    } else {
      throw IllegalStateException("매도 주문이 주문을 요청한 상태가 아닙니다.")
    }
  }

  private fun earnPrice(): BigDecimal {
    // 매도 주문이 존재하는지 검사
    this.sell ?: throw IllegalStateException("매도 주문이 없습니다. 가격을 계산할 수 없습니다. 전체 값 : $this")
    return this.sell.totalPrice() - this.buy.totalPrice() - (this.sell.paidFee + this.buy.paidFee)
  }

  fun toMsg(): TradeResultMessage = run {
    this.sell ?: throw IllegalStateException("매도 주문이 없습니다. 메시지를 생성할 수 없습니다. 전체 값 : $this")
    TradeResultMessage(
      TradeResultMsgKey(this.buy.uuid, this.earnPrice()),
      TradeResultMsgValue(this.buy, this.sell)
    )
  }

  companion object {
    fun createFromBuy(buy: OrderResponse): TradeResult = TradeResult(buy)
  }
}