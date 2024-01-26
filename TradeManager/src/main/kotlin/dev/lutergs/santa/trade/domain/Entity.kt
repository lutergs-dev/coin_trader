package dev.lutergs.santa.trade.domain

import dev.lutergs.santa.trade.infra.impl.OrderEntity
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.format.DateTimeFormatter

data class KubernetesInfo (
  val namespace: String,
  val imagePullSecretName: String,
  val imagePullPolicy: String,
  val imageName: String,
  val envSecretName: String
)

data class WorkerConfig (
  val profitPercent: Double,
  val lossPercent: Double,
  val waitHour: Long
)

data class Message (
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