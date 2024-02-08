package dev.lutergs.santa.trade.worker

import com.influxdb.v3.client.InfluxDBClient
import com.influxdb.v3.client.Point
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class InfluxTest {

  @Test
  fun `데이터 입출력 검증`() {
//    val client = InfluxDBClient.getInstance(
//      "https://us-east-1-1.aws.cloud2.influxdata.com",
//      "tgVNJbZfyIpp8ayg7TyEP990a-oZFdhVE71LbgKISdjSTHOIzdgbmk1CVIzTNsqLtppJRoTsZ3MQ-dUJ_5fvaA==".toCharArray(),
//      "coin-trader"
//    )
//
//    val p = Point.measurement("test")
//      .setTags(mutableMapOf(
//        "type" to "test",
//        "coin" to "BTC"
//      )).setFields(mutableMapOf<String, Any>(
//        "price" to 123456,
//        "uuid" to "test-uuid"
//      )).setTimestamp(OffsetDateTime.now().toInstant())
//
//    client.writePoint(p)
//
//    client.queryPoints("SELECT * FROM \"test\"")
//      .forEach {
//        println("data is - tag : ${it.tagNames}, ${it.getTag("type")}, ${it.getTag("coin")}")
//        println("        - field : ${it.fieldNames}, ${it.getField("price") as Int}, ${it.getField("uuid") as String}")
//        println("        - timestamp : ${it.timestamp}")
//      }
  }
}