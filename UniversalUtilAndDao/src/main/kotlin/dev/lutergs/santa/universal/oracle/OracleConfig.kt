package dev.lutergs.santa.universal.oracle

import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactories
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.ConnectionFactoryOptions
import oracle.r2dbc.OracleR2dbcOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration


@Configuration
class OracleConfig(
  @Value("\${custom.universal-util-and-dao.oracle.descriptor}") private val descriptor: String,
  @Value("\${custom.universal-util-and-dao.oracle.username}") private val username: String,
  @Value("\${custom.universal-util-and-dao.oracle.password}") private val password: String,
  @Value("\${custom.universal-util-and-dao.oracle.max-conn}") private val maxConn: Int,
  @Value("\${custom.universal-util-and-dao.oracle.min-conn}") private val minConn: Int,
): AbstractR2dbcConfiguration() {

  @Bean
  override fun connectionFactory(): ConnectionFactory {
    return ConnectionFactoryOptions.builder()
      // DESCRIPTOR invalidate these options - HOST, PORT, DATABASE, SSL
      .option(OracleR2dbcOptions.DESCRIPTOR, this.descriptor)
      .option(ConnectionFactoryOptions.DRIVER, "pool")
      .option(ConnectionFactoryOptions.PROTOCOL, "oracle")
      .option(ConnectionFactoryOptions.USER, this.username)
      .option(ConnectionFactoryOptions.PASSWORD, this.password)
      .let { ConnectionFactories.get(it.build()) }
      .let { ConnectionPool(
        ConnectionPoolConfiguration.builder(it)
        .maxSize(this.maxConn)
        .minIdle(this.minConn)
        .build())
      }
  }

  override fun getCustomConverters(): List<Any> {
    return listOf(
      SellTypeToStringConverter(),
      StringToSellTypeConverter()
    )
  }
}