package dev.lutergs.santa.trade.domain

data class KubernetesInfo(
  val namespace: String,
  val imagePullSecretName: String,
  val imagePullPolicy: String,
  val imageName: String,
  val envSecretName: String
)

data class WorkerConfig(
  val phase1: Phase1,
  val phase2: Phase2,
  val initMaxMoney: Int,
  val initMinMoney: Int
)

data class Phase1(
  val waitMinute: Long,
  val profitPercent: Double,
  val lossPercent: Double
)

data class Phase2(
  val waitMinute: Long,
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