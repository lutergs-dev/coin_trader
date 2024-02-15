package dev.lutergs.santa.trade.manager.infra.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.infra.impl.AlertMessageSenderImpl
import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.trade.manager.infra.impl.KubernetesInfo
import dev.lutergs.santa.trade.manager.domain.entity.Phase
import dev.lutergs.santa.trade.manager.domain.entity.WorkerConfig
import dev.lutergs.santa.trade.manager.infra.impl.WorkerControllerImpl
import dev.lutergs.santa.trade.manager.service.AnalyticService
import dev.lutergs.santa.trade.manager.service.DangerControlService
import dev.lutergs.santa.trade.manager.service.ManagerService
import dev.lutergs.santa.trade.manager.service.TradeResultService
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(value = [
  UpbitSpringConfig::class,
  KubernetesSpringConfig::class,
  SellSpringConfig::class,
  WorkerSpringConfig::class,
  MessageSenderConfig::class
])
class SpringConfig(
  private val upbitSpringConfig: UpbitSpringConfig,
  private val kubernetesSpringConfig: KubernetesSpringConfig,
  private val sellSpringConfig: SellSpringConfig,
  private val workerSpringConfig: WorkerSpringConfig,
  private val messageSenderConfig: MessageSenderConfig
) {

  @Bean
  fun upbitClient(): BasicClient = BasicClient(
    accessKey = this.upbitSpringConfig.accessKey,
    secretKey = this.upbitSpringConfig.secretKey
  )

  @Bean
  fun kubernetesInfo(): KubernetesInfo = KubernetesInfo(
    namespace = this.kubernetesSpringConfig.namespace,
    imagePullSecretName = this.kubernetesSpringConfig.image.pullSecretName,
    imagePullPolicy = this.kubernetesSpringConfig.image.pullPolicy,
    imageName = this.kubernetesSpringConfig.image.name,
    envSecretName = this.kubernetesSpringConfig.envSecretName
  )

  @Bean
  fun workerConfig(): WorkerConfig = WorkerConfig(
    phase1 = Phase(
      waitMinute = this.sellSpringConfig.phase1.waitMinute,
      profitPercent = this.sellSpringConfig.phase1.profitPercent,
      lossPercent = this.sellSpringConfig.phase1.lossPercent
    ),
    phase2 = Phase(
      waitMinute = this.sellSpringConfig.phase2.waitMinute,
      profitPercent = this.sellSpringConfig.phase2.profitPercent,
      lossPercent = this.sellSpringConfig.phase2.lossPercent
    ),
    initMaxMoney = this.workerSpringConfig.maxMoney,
    initMinMoney = this.workerSpringConfig.minMoney
  )

  @Bean
  fun analysticService(
    tradeResultRepository: TradeResultRepository,
    messageSender: AlertMessageSender
  ): AnalyticService = AnalyticService(tradeResultRepository, messageSender)
  
  @Bean
  fun managerService(
    dangerControlService: DangerControlService,
    client: BasicClient,
    dangerCoinRepository: DangerCoinRepository,
    workerConfig: WorkerConfig,
    workerController: WorkerController
  ): ManagerService = ManagerService(
    dangerControlService, dangerCoinRepository, client, workerController, workerConfig
  )
  
  @Bean
  fun dangerControlService(
    dangerCoinRepository: DangerCoinRepository,
    messageSender: AlertMessageSender,
    objectMapper: ObjectMapper,
  ): DangerControlService = DangerControlService(
    dangerCoinRepository,
    messageSender, 
    objectMapper
  )

  @Bean
  fun tradeResultService(
    tradeResultRepository: TradeResultRepository,
    objectMapper: ObjectMapper
  ): TradeResultService = TradeResultService(tradeResultRepository, objectMapper)

  @Bean
  fun alertMessageSender(
    objectMapper: ObjectMapper
  ): AlertMessageSenderImpl = AlertMessageSenderImpl(
    baseUrl = this.messageSenderConfig.url,
    username = this.messageSenderConfig.username,
    password = this.messageSenderConfig.password,
    topicName = this.messageSenderConfig.topic,
    objectMapper = objectMapper
  )

  @Bean
  fun workerControllerImpl(
    kubernetesInfo: KubernetesInfo
  ): WorkerControllerImpl = WorkerControllerImpl(kubernetesInfo)
}