package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.AlarmMessage
import dev.lutergs.santa.trade.worker.domain.entity.TradeResult
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.util.*

class MessageSenderKafkaProxyImpl(
  kafkaProxyUrl: String,
  kafkaClusterName: String,
  kafkaApiKey: String,
  kafkaApiSecret: String,
  private val alarmTopicName: String,
  private val tradeResultTopicName: String,
  private val objectMapper: ObjectMapper
): MessageSender {

  private val webClient = WebClient.builder()
    .baseUrl("$kafkaProxyUrl/kafka/v3/clusters/$kafkaClusterName/topics")
    .defaultHeader("Content-Type", "application/json")
    .defaultHeader(
      "Authorization",
      Base64.getEncoder()
        .encodeToString("$kafkaApiKey:$kafkaApiSecret".toByteArray(Charsets.UTF_8))
        .let { "Basic $it" }
    ).build()

  override fun sendAlarm(msg: AlarmMessage): Mono<KafkaMessageResponse> {
    return this.webClient
      .post()
      .uri { it.path("/${this.alarmTopicName}/records").build() }
      .bodyValue(KafkaMessage(msg.key, msg.value).toJsonString(this.objectMapper))
      .retrieve()
      .bodyToMono(KafkaMessageResponse::class.java)
  }

  override fun sendTradeResult(msg: TradeResult): Mono<KafkaMessageResponse> {
    return this.webClient
      .post()
      .uri { it.path("/${this.tradeResultTopicName}/records").build() }
      .bodyValue(KafkaMessage(msg.key, msg.value).toJsonString(this.objectMapper))
      .retrieve()
      .bodyToMono(KafkaMessageResponse::class.java)
  }
}

data class KafkaMessage(
  val key: Any,
  val value: Any
) {
  fun toJsonString(mapper: ObjectMapper): String {
    return "{\"key\":{\"type\":\"JSON\",\"data\":${mapper.writeValueAsString(this.key)}}," +
      "\"value\":{\"type\":\"JSON\",\"data\":${mapper.writeValueAsString(this.value)}}}"
  }
}

data class KafkaMessageResponse (
  @JsonProperty("error_code") val errorCode: Int,
  @JsonProperty("cluster_id") val clusterId: String,
  @JsonProperty("topic_name") val topicName: String,
  @JsonProperty("partition_id") val partitionId: Int,
  @JsonProperty("offset") val offset: Int,
  @JsonProperty("timestamp") val timestamp: String, // TODO: 차후 deserializer 만들기
  @JsonProperty("key") val key: KafkaMessageKVResponse,
  @JsonProperty("value") val value: KafkaMessageKVResponse
)

data class KafkaMessageKVResponse (
  @JsonProperty("type") val type: String,
  @JsonProperty("size") val size: Int
)