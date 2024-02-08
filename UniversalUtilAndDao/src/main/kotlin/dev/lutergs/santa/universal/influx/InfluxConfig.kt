package dev.lutergs.santa.universal.influx

import com.influxdb.v3.client.InfluxDBClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class InfluxConfig {

  @Bean
  fun influxDbClient(
    @Value("\${custom.universal-util-and-dao.influx.host-url}") hostUrl: String,
    @Value("\${custom.universal-util-and-dao.influx.token}") token: String,
    @Value("\${custom.universal-util-and-dao.influx.database}") database: String
  ): InfluxDBClient {
    return InfluxDBClient.getInstance(hostUrl, token.toCharArray(), database)
  }
}