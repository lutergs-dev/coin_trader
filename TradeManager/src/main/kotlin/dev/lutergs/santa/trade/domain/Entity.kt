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

data class Message (
  val topic: String,
  val title: String,
  val body: String
) {
  companion object {

    private val dateTimeFormatter = DateTimeFormatter.ofPattern("HH'시' mm'분'")

    fun createDangerCoinMessage(topic: String, coinName: String): Message {
      return Message(
        topic = topic,
        title = "$coinName 코인을 손실매도했습니다.",
        body = "$coinName 코인을 손실매도했습니다. 해당 코인은 24시간동안 거래하지 않습니다."
      )
    }

    fun createProfitMessage(orders: Flux<OrderEntity>, topic: String): Mono<Message> {
      return orders
        .sort { o1, o2 -> (o1.buyPlaceAt!!.toEpochSecond() - o2.buyPlaceAt!!.toEpochSecond()).toInt() }
        .collectList()
        .flatMap { orderEntities ->
          Mono.fromCallable {
            orderEntities
              .filter {
                it.buyPlaceAt != null &&
                it.coin != null &&
                it.buyPrice != null &&
                it.sellPrice != null &&
                it.buyFinishAt != null &&
                it.sellFinishAt != null &&
                it.profit != null
              }.joinToString(separator = "\n") {
                val isProfit = (if (it.buyPrice!! < it.sellPrice!!) "이득" else "손해")
                val sellTypeStr = when {
                  it.sellFinishAt!!.minusHours(6).isAfter(it.buyFinishAt!!) -> "시간초과"
                  it.buyPrice!! < it.sellPrice!! -> "익절"
                  it.buyPrice!! > it.sellPrice!! -> "손절"
                  else -> "동가격"
                }
                "[${it.buyPlaceAt!!.toLocalDateTime().format(this.dateTimeFormatter)}]" +
                  " ${it.coin!!} ${it.buyPrice!!.toStrWithPoint()} 에 매수," +
                  " ${it.sellPrice!!.toStrWithPoint()} 에 $sellTypeStr 매도. ${it.profit!!.toStrWithPoint()} 원 $isProfit"
              }.let { body ->
                Message(
                  topic = topic,
                  title = "최근 24시간 동안 ${orderEntities.sumOf { it.profit ?: 0.0 }.toStrWithPoint()} 원을 벌었습니다.",
                  body = "코인 매수/매도기록은 다음과 같습니다.\n\n$body"
                )
              }
          }
        }
    }
  }
}