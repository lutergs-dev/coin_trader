package dev.lutergs.santa.trade.worker.infra

import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.DangerCoinMessage
import dev.lutergs.santa.trade.worker.domain.entity.TradeResultMessage
import reactor.core.publisher.Mono

class MessageSenderKafkaProxyImpl(
  private val kafkaMessageSender: KafkaProxyMessageSender,
  private val alarmTopicName: String,
  private val tradeResultTopicName: String
): MessageSender {

  override fun sendAlarm(msg: DangerCoinMessage): Mono<KafkaMessageResponse> {
    return this.kafkaMessageSender.sendPost(this.alarmTopicName, KafkaMessage(msg.key, msg.value))
  }

  override fun sendTradeResult(msg: TradeResultMessage): Mono<KafkaMessageResponse> {
    return this.kafkaMessageSender.sendPost(this.tradeResultTopicName, KafkaMessage(msg.key, msg.value))
  }
}

