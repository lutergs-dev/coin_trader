package dev.lutergs.upbitclient

import dev.lutergs.upbitclient.api.quotation.orderbook.OrderStep
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

class TestBigDecimal {

  @Test
  fun testNegativeSetScale() {
    val t = BigDecimal(12512640.2341)
    t.setScale(-3, RoundingMode.HALF_UP)
      .stripTrailingZeros()
      .let { println(it.toPlainString()) }

    ((t / BigDecimal(500)).setScale(0, RoundingMode.HALF_UP) * BigDecimal(500))
      .let { println(it.toPlainString()) }

    OrderStep.calculateOrderStepPrice(BigDecimal(0.079182478))
      .let { println(it.toPlainString()) }
  }
}