package dev.lutergs.santa.universal

import com.influxdb.v3.client.InfluxDBClient
import dev.lutergs.santa.universal.oracle.SellTypeToStringConverter
import dev.lutergs.santa.universal.oracle.StringToSellTypeConverter
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import oracle.r2dbc.OracleR2dbcOptions
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.mongodb.repository.config.EnableReactiveMongoRepositories
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories

/**
 * Configuration Properties for Oracle Database. Designed for use in Oracle Cloud Autonomous Database
 * */
@ConfigurationProperties(prefix = "universal.db.oracle")
data class OracleConfig(
  /**
   * Oracle connection descriptor. Can be found in Oracle Cloud Autonomous Database connection page
   * */
  val descriptor: String,

  /**
   * Oracle DB username
   * */
  val username: String,

  /**
   * Oracle DB user's password
   * */
  val password: String,

  /**
   * max connection pool number. Must be higher than min-conn
   * */
  val maxConn: Int,

  /**
   * min connection pool number. Must be higher than 0 (1 is good option)
   * */
  val minConn: Int
)

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
@EnableAutoConfiguration(exclude = [R2dbcAutoConfiguration::class])
@EnableR2dbcRepositories(value = ["dev.lutergs.santa"])
@EnableReactiveMongoRepositories(value = ["dev.lutergs.santa"])
@EnableConfigurationProperties(value = [InfluxConfig::class, OracleConfig::class])
class UniversalDataConfig(
  private val oracleConfig: OracleConfig,
  private val influxConfig: InfluxConfig
): AbstractR2dbcConfiguration() {

  override fun connectionFactory(): ConnectionFactory {
    return ConnectionFactoryOptions.builder()
      // DESCRIPTOR invalidate these options - HOST, PORT, DATABASE, SSL
      .option(OracleR2dbcOptions.DESCRIPTOR, this.oracleConfig.descriptor)
      .option(ConnectionFactoryOptions.DRIVER, "pool")
      .option(ConnectionFactoryOptions.PROTOCOL, "oracle")
      .option(ConnectionFactoryOptions.USER, this.oracleConfig.username)
      .option(ConnectionFactoryOptions.PASSWORD, this.oracleConfig.password)
      .let { ConnectionFactories.get(it.build()) }
      .let { ConnectionPool(
        ConnectionPoolConfiguration.builder(it)
          .maxSize(this.oracleConfig.maxConn)
          .minIdle(this.oracleConfig.minConn)
          .build())
      }
  }

  override fun getCustomConverters(): List<Any> {
    return listOf(
      SellTypeToStringConverter(),
      StringToSellTypeConverter()
    )
  }

  @Bean
  fun influxDbClient(): InfluxDBClient {
    return InfluxDBClient.getInstance(
      this.influxConfig.hostUrl, this.influxConfig.token.toCharArray(), this.influxConfig.database
    )
  }
}