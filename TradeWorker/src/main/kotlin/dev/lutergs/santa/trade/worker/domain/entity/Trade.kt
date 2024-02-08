package dev.lutergs.santa.trade.worker.domain.entity

import dev.lutergs.upbitclient.dto.MarketCode
import java.math.BigDecimal

data class MainTrade(
  val market: MarketCode,
  val money: Int
)

data class TradePhase(
  val phase1: Phase,
  val phase2: Phase
) {
  fun totalWaitMinute(): Long {
    return phase1.waitMinute + phase2.waitMinute
  }
}

data class Phase(
  val waitMinute: Long,
  val profitPercent: BigDecimal,
  val lossPercent: BigDecimal
) {
  fun getProfitPrice(price: BigDecimal): BigDecimal {
    return price * (BigDecimal(1.0) + (this.profitPercent * BigDecimal(0.01)))
  }

  fun getLossPrice(price: BigDecimal): BigDecimal {
    return price * (BigDecimal(1.0) + (this.lossPercent * BigDecimal(0.01)))
  }
}


