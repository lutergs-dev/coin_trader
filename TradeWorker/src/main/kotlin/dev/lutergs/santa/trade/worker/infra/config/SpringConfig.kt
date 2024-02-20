package dev.lutergs.santa.trade.worker.infra.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import dev.lutergs.santa.trade.worker.domain.CoinPriceTracker
import dev.lutergs.santa.trade.worker.domain.Manager
import dev.lutergs.santa.trade.worker.domain.MessageSender
import dev.lutergs.santa.trade.worker.domain.Trader
import dev.lutergs.santa.trade.worker.domain.entity.MainTrade
import dev.lutergs.santa.trade.worker.domain.entity.TradePhase
import dev.lutergs.santa.trade.worker.domain.entity.Phase
import dev.lutergs.santa.trade.worker.infra.KafkaProxyMessageSender
import dev.lutergs.santa.trade.worker.infra.impl.CoinPriceTrackerImpl
import dev.lutergs.santa.trade.worker.infra.impl.ManagerImpl
import dev.lutergs.santa.trade.worker.infra.impl.MessageSenderKafkaProxyImpl
import dev.lutergs.santa.trade.worker.infra.impl.TraderImpl
import dev.lutergs.santa.trade.worker.infra.repository.MongoCoinPriceReactiveRepository
import dev.lutergs.santa.trade.worker.service.WorkerService
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

@Configuration
@EnableConfigurationProperties(value = [
  TradeConfig::class,
  KafkaConfig::class,
  UpbitConfig::class,
  ManagerConfig::class
])
class SpringConfig(
  private val tradeConfig: TradeConfig,
  private val kafkaConfig: KafkaConfig,
  private val upbitConfig: UpbitConfig,
  private val managerConfig: ManagerConfig
) {

  @Bean
  fun mainTrade(): MainTrade = MainTrade(
    market = this.tradeConfig.startMarket.split("-").let { MarketCode(it[0], it[1]) },
    money = this.tradeConfig.startMoney
  )


  @Bean
  fun kafkaProxyMessageSender(
    objectMapper: ObjectMapper
  ): KafkaProxyMessageSender = KafkaProxyMessageSender(
    kafkaProxyUrl = this.kafkaConfig.url,
    kafkaClusterName = this.kafkaConfig.cluster.name,
    kafkaApiKey = this.kafkaConfig.api.key,
    kafkaApiSecret = this.kafkaConfig.api.secret,
    objectMapper
  )

  @Bean
  fun messageSender(
    kafkaProxyMessageSender: KafkaProxyMessageSender
  ): MessageSender = MessageSenderKafkaProxyImpl(
    kafkaProxyMessageSender,
    alarmTopicName = this.kafkaConfig.topic.alarm,
    tradeResultTopicName = this.kafkaConfig.topic.tradeResult
  )

  @Bean
  fun traderImpl(
    kafkaProxyMessageSender: KafkaProxyMessageSender,
    client: BasicClient
  ): TraderImpl = TraderImpl(
    kafkaProxyMessageSender,
    topicName = this.kafkaConfig.topic.tradeResult,
    client,
    watchIntervalSecond = this.tradeConfig.watch.interval
  )

  @Bean
  fun managerImpl(): ManagerImpl = ManagerImpl(
    managerUrl = this.managerConfig.url
  )

  @Bean
  fun coinPriceTrackerImpl(
    basicClient: BasicClient,
    repository: MongoCoinPriceReactiveRepository
  ): CoinPriceTrackerImpl = CoinPriceTrackerImpl(
    basicClient, repository
  )

  @Bean
  fun upbitClient() = BasicClient(
    accessKey = this.upbitConfig.accessKey,
    secretKey = this.upbitConfig.secretKey
  )

  @Bean
  fun objectMapper(): ObjectMapper = ObjectMapper()
    .registerKotlinModule()
    .registerModule(JavaTimeModule())
  
  @Bean
  fun phaseInfo(): TradePhase = TradePhase(
    phase1 = Phase(
      waitMinute = this.tradeConfig.sell.phase1.waitMinute,
      profitPercent = BigDecimal(this.tradeConfig.sell.phase1.profitPercent),
      lossPercent = BigDecimal(this.tradeConfig.sell.phase1.lossPercent)
    ),
    phase2 = Phase(
      waitMinute = this.tradeConfig.sell.phase2.waitMinute,
      profitPercent = BigDecimal(this.tradeConfig.sell.phase2.profitPercent),
      lossPercent = BigDecimal(this.tradeConfig.sell.phase2.lossPercent)
    )
  )

  @Bean
  fun workerService(
    mainTrade: MainTrade,
    tradePhase: TradePhase,
    trader: Trader,
    priceTracker: CoinPriceTracker,
    alarmSender: MessageSender,
    manager: Manager,
    applicationContext: ApplicationContext
  ): WorkerService = WorkerService(
    mainTrade = mainTrade,
    tradePhase = tradePhase,
    watchIntervalSecond = this.tradeConfig.watch.interval.toInt(),
    movingAverageBigCount = this.tradeConfig.sell.profitMovingAverage.big,
    movingAverageSmallCount = this.tradeConfig.sell.profitMovingAverage.small,
    trader = trader,
    priceTracker = priceTracker,
    alarmSender = alarmSender,
    manager = manager,
    applicationContext = applicationContext
  )
}

