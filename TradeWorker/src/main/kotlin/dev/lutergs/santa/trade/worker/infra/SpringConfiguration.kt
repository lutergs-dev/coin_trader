package dev.lutergs.santa.trade.worker.infra

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.entity.TradePhase
import dev.lutergs.santa.trade.worker.domain.entity.Phase
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.webclient.BasicClient
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
    @Value("\${custom.upbit.secret-key}") secretKey: String
  ) = BasicClient(accessKey, secretKey)

  @Bean
  fun objectMapper(): ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())
  
  @Bean
  fun phaseInfo(
    @Value("\${custom.trade.sell.phase1.wait-minute}") p1WaitMinute: Long,
    @Value("\${custom.trade.sell.phase1.profit-percent}") p1ProfitPercent: Double,
    @Value("\${custom.trade.sell.phase1.loss-percent}") p1LossPercent: Double,
    @Value("\${custom.trade.sell.phase2.wait-minute}") p2WaitMinute: Long,
    @Value("\${custom.trade.sell.phase2.profit-percent}") p2ProfitPercent: Double,
    @Value("\${custom.trade.sell.phase2.loss-percent}") p2LossPercent: Double,
  ): TradePhase = TradePhase(
    Phase(p1WaitMinute, p1ProfitPercent, p1LossPercent),
    Phase(p2WaitMinute, p2ProfitPercent, p2LossPercent)
  )
}

