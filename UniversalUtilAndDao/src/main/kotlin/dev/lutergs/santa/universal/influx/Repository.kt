package dev.lutergs.santa.universal.influx

import com.influxdb.v3.client.InfluxDBClient
import org.springframework.stereotype.Repository




@Repository
class CoinTimeDataRepository(
  private val client: InfluxDBClient
) {
  private val measurement = "trade-result"

  fun saveSnapshot(snapshot: CoinTradeSnapshot) {
    this.client.writePoint(snapshot.toPoint())
  }

  // aggregate function 도 여기다가...
}