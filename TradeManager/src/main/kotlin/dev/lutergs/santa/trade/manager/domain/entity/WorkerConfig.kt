package dev.lutergs.santa.trade.manager.domain.entity

import java.math.BigDecimal
import java.math.RoundingMode

data class WorkerConfig(
  val phase1: Phase,
  val phase2: Phase,
  val initMaxMoney: Long,
  val initMinMoney: Long
) {
  companion object {
    private val actualPercentage: BigDecimal = BigDecimal("99.5")   // 구매시 수수료를 고려한 조치
  }
  fun actualInitMaxMoney(money: Long? = null): Long {
    return (BigDecimal(money ?: this.initMaxMoney) * actualPercentage)
      .setScale(0, RoundingMode.HALF_UP)
      .toLong()
  }
}

data class Phase(
  val waitMinute: Long,
  val profitPercent: BigDecimal,
  val lossPercent: BigDecimal
)



