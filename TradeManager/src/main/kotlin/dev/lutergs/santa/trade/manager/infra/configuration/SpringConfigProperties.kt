package dev.lutergs.santa.trade.manager.infra.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.math.BigDecimal


@ConfigurationProperties(prefix = "custom.upbit")
data class UpbitSpringConfig(
  /**
   * URL of InfluxDB
   * */
  val accessKey: String,

  /**
   * Token of InfluxDB
   * */
  val secretKey: String
)

@ConfigurationProperties(prefix = "custom.kubernetes")
data class KubernetesSpringConfig(
  val namespace: String,
  @NestedConfigurationProperty val image: KubernetesImageConfig,
  val envSecretName: String,
)

data class KubernetesImageConfig(
  val pullSecretName: String,
  val pullPolicy: String,
  val name: String
)

@ConfigurationProperties(prefix = "custom.trade.sell")
data class SellSpringConfig(
  @NestedConfigurationProperty val phase1: Phase,
  @NestedConfigurationProperty val phase2: Phase
)


data class Phase(
  val waitMinute: Long,
  val profitPercent: BigDecimal,
  val lossPercent: BigDecimal
)

@ConfigurationProperties(prefix = "custom.trade.worker")
data class WorkerSpringConfig(
  val maxMoney: Long,
  val minMoney: Long
)

@ConfigurationProperties(prefix = "custom.message-sender")
data class MessageSenderConfig(
  val url: String,
  val username: String,
  val password: String,
  val topic: String
)