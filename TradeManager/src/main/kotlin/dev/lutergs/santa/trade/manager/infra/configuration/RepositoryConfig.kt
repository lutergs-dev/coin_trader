package dev.lutergs.santa.trade.manager.infra.configuration

import com.influxdb.v3.client.InfluxDBClient
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration Properties for InfluxDB. Designed for use in InfluxData Cloud
 * */
@ConfigurationProperties(prefix = "universal.db.influx")
data class InfluxConfig(
  /**
   * URL of InfluxDB
   * */
  val hostUrl: String,

  /**
   * Token of InfluxDB
   * */
  val token: String,

  /**
   * Database (in InfluxData Cloud, Bucket) name
   * */
  val database: String
)


@Configuration
@EnableConfigurationProperties(value = [InfluxConfig::class])
class UniversalDataConfig(
  private val influxConfig: InfluxConfig
) {

  @Bean
  fun influxDbClient(): InfluxDBClient {
    return InfluxDBClient.getInstance(
      this.influxConfig.hostUrl, this.influxConfig.token.toCharArray(), this.influxConfig.database
    )
  }
}