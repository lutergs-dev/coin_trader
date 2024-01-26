package dev.lutergs.santa.trade.infra

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.domain.*
import dev.lutergs.santa.trade.infra.impl.AlertMessageSenderImpl
import dev.lutergs.santa.trade.service.AlertService
import dev.lutergs.santa.trade.service.ManagerService
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SpringConfiguration {

  @Bean
  fun upbitClient(
    @Value("\${custom.upbit.access-key}") accessKey: String,
    @Value("\${custom.upbit.secret-key}") secretKey: String
  ): BasicClient = BasicClient(accessKey, secretKey)

  @Bean
  fun kubernetesInfo(
    @Value("\${custom.kubernetes.namespace}") namespace: String,
    @Value("\${custom.kubernetes.image.pull-secret-name}") imagePullSecretName: String,
    @Value("\${custom.kubernetes.image.pull-policy}") imagePullPolicy: String,
    @Value("\${custom.kubernetes.image.name}") imageName: String,
    @Value("\${custom.kubernetes.env-secret-name}") envSecretName: String
  ): KubernetesInfo {
    return KubernetesInfo(namespace, imagePullSecretName, imagePullPolicy, imageName, envSecretName)
  }

  @Bean
  fun workerConfig(
    @Value("\${custom.trade.sell.profit-percent}") profitPercent: Double,
    @Value("\${custom.trade.sell.loss-percent}") lossPercent: Double,
    @Value("\${custom.trade.sell.wait-hour}") waitHour: Long
  ): WorkerConfig = WorkerConfig(
    profitPercent, lossPercent, waitHour
  )
  
  @Bean
  fun managerService(
    alertService: AlertService,
    client: BasicClient,
    dangerCoinRepository: DangerCoinRepository,
    kubernetesInfo: KubernetesInfo,
    workerConfig: WorkerConfig,
    @Value("\${custom.trade.worker.max-money}") maxMoney: Int,
    @Value("\${custom.trade.worker.min-money}") minMoney: Int
  ): ManagerService = ManagerService(
    alertService, client, dangerCoinRepository, kubernetesInfo, workerConfig, maxMoney, minMoney
  )
  
  @Bean
  fun alertService(
    dangerCoinRepository: DangerCoinRepository,
    tradeHistoryRepository: TradeHistoryRepository,
    messageSender: AlertMessageSender,
    objectMapper: ObjectMapper,
    @Value("\${custom.message-sender.topic}") topicName: String
  ): AlertService = AlertService(
    dangerCoinRepository,
    tradeHistoryRepository,
    messageSender, 
    objectMapper, 
    topicName
  )

  @Bean
  fun alertMessageSender(
    @Value("\${custom.message-sender.url}") baseUrl: String,
    @Value("\${custom.message-sender.username}") username: String,
    @Value("\${custom.message-sender.password}") password: String,
    objectMapper: ObjectMapper
  ): AlertMessageSenderImpl = AlertMessageSenderImpl(baseUrl, username, password, objectMapper)
}