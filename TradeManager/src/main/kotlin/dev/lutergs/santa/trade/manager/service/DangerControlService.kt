package dev.lutergs.santa.trade.manager.service

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.trade.manager.domain.entity.Message
import dev.lutergs.santa.util.toStrWithStripTrailing
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import reactor.core.publisher.Mono
import java.time.OffsetDateTime

class DangerControlService(
  private val tradeResultRepository: TradeResultRepository,
  private val dangerCoinRepository: DangerCoinRepository,
  private val messageSender: AlertMessageSender,
  private val objectMapper: ObjectMapper
) {

  // consumer 쪽에선 받은 데이터 mongoDB 에 저장 및 24 시간동안 3번 이상의 손실을 겪으면 메시지 후 거래중단
  @KafkaListener(topics = ["\${custom.kafka.topic.danger-coin}"])
  fun consume(record: ConsumerRecord<String, String>) {
    this.objectMapper.readTree(record.key())
      .path("coinName").asText()
      .let { this.dangerCoinRepository.setDangerCoin(it) }
      .flatMap { this.dangerCoinRepository.getDangerCoins().collectList() }
      .filter { it.size > 3 }
      .flatMap { dangerCoins ->
        this.tradeResultRepository.getAllResultAfterDateTime(OffsetDateTime.now().minusHours(24))
          .filter { it.sellType.isFinished() }
          .collectList()
          .flatMap { t -> t.sumOf { it.profit!! }.let { Mono.just(it) } }
          .flatMap { profitSum ->
            Message(
              title = "[긴급] 최근 24시간동안 ${dangerCoins.size}번 하락 발생",
              body = "${dangerCoins.sortedBy { it.createdAt }.joinToString(separator = "\n") { it.toInfoString() }}\n\n24시간 총수입 : ${profitSum.toStrWithStripTrailing()}"
            ).let { m -> Mono.just(m) }
          }
      }.flatMap { this.messageSender.sendMessage(it) }
      .block()
  }


  fun sendAllCoinsAreDangerous(): Mono<String> {
    return this.messageSender.sendMessage(
      Message("모든 코인이 위험합니다.", "모든 코인이 위험합니다. 업비트를 확인 후 조치를 취해주세요.")
    )
  }
}