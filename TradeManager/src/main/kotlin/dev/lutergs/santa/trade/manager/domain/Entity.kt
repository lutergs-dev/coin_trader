package dev.lutergs.santa.trade.manager.domain

import dev.lutergs.santa.universal.oracle.SellType
import dev.lutergs.santa.universal.util.toStrWithScale
import dev.lutergs.upbitclient.dto.MarketCode
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class KubernetesInfo(
  val namespace: String,
  val imagePullSecretName: String,
  val imagePullPolicy: String,
  val imageName: String,
  val envSecretName: String
)

data class WorkerConfig(
  val phase1: Phase,
  val phase2: Phase,
  val initMaxMoney: Long,
  val initMinMoney: Long
)

data class Phase(
  val waitMinute: Long,
  val profitPercent: BigDecimal,
  val lossPercent: BigDecimal
)

data class Message(
  val topic: String,
  val title: String,
  val body: String
) {
  companion object {

    fun createDangerCoinMessage(topic: String, coinName: String): Message {
      return Message(
        topic = topic,
        title = "$coinName 코인을 손실매도했습니다.",
        body = "$coinName 코인을 손실매도했습니다. 해당 코인은 24시간동안 거래하지 않습니다."
      )
    }
  }
}

data class CompleteOrderResult(
  val coin: MarketCode,
  val buy: OrderResult,
  val sell: OrderResult?,
  val profit: BigDecimal?,
  val sellType: SellType
) {
  fun toInfoString(): String {
    return if (sellType.isFinished()) {
      val isProfit = if (this.buy.price < this.sell!!.price) "이득" else "손해"
      "[${this.buy.placeAt.toLocalDateTime().format(dateTimeFormatter)}] " +
        "${this.coin.quote} ${this.buy.price.toStrWithScale()} 에 매수, " +
        "${this.sell.price.toStrWithScale()} 에 ${this.sellType.toInfoString()} 매도, ${this.profit!!.toStrWithScale()} 원 $isProfit"
    } else {
      // 주문완료되지 않은 것에 대한 String은...?
      "[${this.buy.placeAt.toLocalDateTime().format(dateTimeFormatter)}] " +
        "${this.coin.quote} ${this.buy.price.toStrWithScale()} 에 매수, " +
        "매도 진행 중"
    }
  }

  companion object {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH'시' mm'분'")
  }
}

data class OrderResult(
  val id: UUID,
  val price: BigDecimal,
  val fee: BigDecimal,
  val volume: BigDecimal,
  val won: BigDecimal,
  val placeAt: OffsetDateTime,
  val finishAt: OffsetDateTime
)

