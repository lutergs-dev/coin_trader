package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.lutergs.santa.trade.worker.domain.UpbitClient
import dev.lutergs.santa.trade.worker.domain.LogRepository
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.upbeatclient.dto.MarketCode
import dev.lutergs.upbeatclient.webclient.BasicClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringConfiguration {

  @Bean
  fun mainTrade(
    @Value("\${custom.trade.start-market}") rawMarket: String,
    @Value("\${custom.trade.start-money}") money: Int
  ): MainTrade {
    return MainTrade(
      market = rawMarket.split("-").let { MarketCode(it[0], it[1]) },
      money = money
    )
  }

  @Bean
  fun messageSender(
    @Value("\${custom.kafka.url}") kafkaUrl: String,
    @Value("\${custom.kafka.cluster.name}") kafkaClusterName: String,
    @Value("\${custom.kafka.api.key}") kafkaApiKey: String,
    @Value("\${custom.kafka.api.secret}") kafkaApiSecret: String,
    @Value("\${custom.kafka.topic.alarm}") alarmTopicName: String,
    @Value("\${custom.kafka.topic.trade-result}") tradeResultTopicName: String,
    objectMapper: ObjectMapper
  ): MessageSender = MessageSenderKafkaProxyImpl(
    kafkaProxyUrl = kafkaUrl,
    kafkaClusterName = kafkaClusterName,
    kafkaApiKey = kafkaApiKey,
    kafkaApiSecret = kafkaApiSecret,
    alarmTopicName = alarmTopicName,
    tradeResultTopicName = tradeResultTopicName,
    objectMapper = objectMapper
  )

  @Bean
  fun upbitClient(
    @Value("\${custom.upbit.access-key}") accessKey: String,
    @Value("\${custom.upbit.secret-key}") secretKey: String,
    repository: LogRepository
  ) = UpbitClient(
    basicClient = BasicClient(accessKey, secretKey),
    repository = repository
  )

  @Bean
  fun objectMapper(): ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())
}

