package dev.lutergs.santa.trade.worker.infra.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "custom.trade")
data class TradeConfig(
  val startMarket: String,
  val startMoney: Int,
  @NestedConfigurationProperty val watch: TradeWatchConfig,
  @NestedConfigurationProperty val sell: TradeSellConfig
)

data class TradeWatchConfig(
  val interval: Long
)

data class TradeSellConfig(
  @NestedConfigurationProperty val phase1: PhaseConfig,
  @NestedConfigurationProperty val phase2: PhaseConfig
)

data class PhaseConfig(
  val waitMinute: Long,
  val profitPercent: String,
  val lossPercent: String
)


@ConfigurationProperties(prefix = "custom.kafka")
data class KafkaConfig(
  val url: String,
  @NestedConfigurationProperty val cluster: KafkaClusterConfig,
  @NestedConfigurationProperty val api: KafkaApiConfig,
  @NestedConfigurationProperty val topic: KafkaTopicConfig
)

data class KafkaClusterConfig(
  val name: String
)

data class KafkaApiConfig(
  val key: String,
  val secret: String
)

data class KafkaTopicConfig(
  val alarm: String,
  val tradeResult: String
)


@ConfigurationProperties(prefix = "custom.upbit")
data class UpbitConfig(
  val accessKey: String,
  val secretKey: String
)

@ConfigurationProperties(prefix = "custom.manager")
data class ManagerConfig(
  val url: String
)