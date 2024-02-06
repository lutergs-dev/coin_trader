package dev.lutergs.santa.trade.domain

import dev.lutergs.upbitclient.dto.MarketCode
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
  val initMaxMoney: Int,
  val initMinMoney: Int
)

data class Phase(
  val waitMinute: Long,
  val profitPercent: Double,
  val lossPercent: Double
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

// TODO : order state 를 관리하는 entity 작성 필요
enum class SellType {
  PROFIT,
  LOSS,
  STOP_PROFIT,
  STOP_LOSS,
  TIMEOUT_PROFIT,
  TIMEOUT_LOSS,
  NOT_COMPLETED,
  NOT_PLACED;
  fun isFinished(): Boolean {
    return when (this) {
      PROFIT, LOSS, STOP_PROFIT, STOP_LOSS, TIMEOUT_PROFIT, TIMEOUT_LOSS -> true
      else -> false
    }
  }
  
  fun toInfoString(): String {
    return when (this) {
      PROFIT -> "1차 익절"
      LOSS -> "1차 손절"
      STOP_PROFIT -> "2차 익절"
      STOP_LOSS -> "2차 손절"
      TIMEOUT_PROFIT -> "시간초과 익절"
      TIMEOUT_LOSS -> "시간초과 손절"
      NOT_COMPLETED -> "매도주문 대기중"
      NOT_PLACED -> "매도주문 없음"
    }
  }
}

data class CompleteOrderResult(
  val coin: MarketCode,
  val buy: OrderResult,
  val sell: OrderResult?,
  val profit: Double?,
  val sellType: SellType
) {
  fun toInfoString(): String {
    return if (sellType.isFinished()) {
      val isProfit = if (this.buy.price < this.sell!!.price) "이득" else "손해"
      "[${this.buy.placeAt.toLocalDateTime().format(dateTimeFormatter)}] " +
        "${this.coin.quote} ${this.buy.price.toStrWithPoint()} 에 매수, " +
        "${this.sell.price.toStrWithPoint()} 에 ${this.sellType.toInfoString()} 매도, ${this.profit!!.toStrWithPoint()} 원 $isProfit"
    } else {
      // 주문완료되지 않은 것에 대한 String은...?
      "[${this.buy.placeAt.toLocalDateTime().format(dateTimeFormatter)}] " +
        "${this.coin.quote} ${this.buy.price.toStrWithPoint()} 에 매수, " +
        "매도 진행 중"
    }
  }

  companion object {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH'시' mm'분'")
  }
}

data class OrderResult(
  val id: UUID,
  val price: Double,
  val fee: Double,
  val volume: Double,
  val won: Double,
  val placeAt: OffsetDateTime,
  val finishAt: OffsetDateTime
)

