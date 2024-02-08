package dev.lutergs.santa.trade.manager.infra

import com.fasterxml.jackson.databind.ObjectMapper
import dev.lutergs.santa.trade.manager.infra.impl.AlertMessageSenderImpl
import dev.lutergs.santa.trade.manager.domain.*
import dev.lutergs.santa.trade.manager.service.AlertService
import dev.lutergs.santa.trade.manager.service.ManagerService
import dev.lutergs.santa.universal.mongo.DangerCoinRepository
import dev.lutergs.upbitclient.webclient.BasicClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal

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
    @Value("\${custom.trade.sell.phase1.wait-minute}") p1WaitMinute: Long,
    @Value("\${custom.trade.sell.phase1.profit-percent}") p1ProfitPercent: String,
    @Value("\${custom.trade.sell.phase1.loss-percent}") p1LossPercent: String,
    @Value("\${custom.trade.sell.phase2.wait-minute}") p2WaitMinute: Long,
    @Value("\${custom.trade.sell.phase2.profit-percent}") p2ProfitPercent: String,
    @Value("\${custom.trade.sell.phase2.loss-percent}") p2LossPercent: String,
    @Value("\${custom.trade.worker.max-money}") maxMoney: Long,
    @Value("\${custom.trade.worker.min-money}") minMoney: Long
  ): WorkerConfig = WorkerConfig(
    Phase(p1WaitMinute, BigDecimal(p1ProfitPercent), BigDecimal(p1LossPercent)),
    Phase(p2WaitMinute, BigDecimal(p2ProfitPercent), BigDecimal(p2LossPercent)),
    maxMoney, minMoney
  )
  
  @Bean
  fun managerService(
    alertService: AlertService,
    client: BasicClient,
    dangerCoinRepository: DangerCoinRepository,
    workerConfig: WorkerConfig,
    workerController: WorkerController
  ): ManagerService = ManagerService(
    alertService, client, dangerCoinRepository, workerController, workerConfig
  )
  
  @Bean
  fun alertService(
    dangerCoinRepository: DangerCoinRepository,
    completeOrderResultRepository: CompleteOrderResultRepository,
    messageSender: AlertMessageSender,
    objectMapper: ObjectMapper,
    @Value("\${custom.message-sender.topic}") topicName: String
  ): AlertService = AlertService(
    dangerCoinRepository,
    completeOrderResultRepository,
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