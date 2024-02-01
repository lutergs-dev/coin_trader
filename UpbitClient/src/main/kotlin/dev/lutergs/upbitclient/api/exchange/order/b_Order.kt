package dev.lutergs.upbitclient.api.exchange.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import dev.lutergs.upbitclient.dto.*
import java.time.OffsetDateTime
import java.util.UUID

// data class of GET https://api.upbit.com/v1/order response

/**
 * 개별 주문 조회 응답 데이터를 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property uuid                   주문의 고유 아이디
 * @property side                   주문 종류
 * @property orderType              주문 방식
 * @property price                  주문 당시 화폐 가격
 * @property state                  주문 상태
 * @property market                 마켓의 유일키
 * @property createdAt              주문 생성 시간
 * @property volume                 사용자가 입력한 주문 양
 * @property remainingVolume        체결 후 남은 주문 양
 * @property reservedFee            수수료로 예약된 비용
 * @property remainingFee           남은 수수료
 * @property paidFee                사용된 수수료
 * @property locked                 거래에 사용중인 비용
 * @property executedVolume         체결된 양
 * @property tradesCount            해당 주문에 걸린 체결 수
 * @property trades                 체결 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderResponse(
  @JsonDeserialize(using = UuidDeserializer::class)
  @JsonProperty("uuid") val uuid: UUID,
  @JsonProperty("side") val side: String,
  @JsonProperty("ord_type") val orderType: String,
  @JsonProperty("price") val price: Double,
  @JsonProperty("state") val state: String,        // done 이 완료
  @JsonSerialize(using = MarketCodeSerializer::class)
  @JsonDeserialize(using = MarketCodeDeserializer::class)
  @JsonProperty("market") val market: MarketCode,
  @JsonSerialize(using = OffsetDateTimeSerializer::class)
  @JsonDeserialize(using = OffsetDateTimeDeserializer::class)
  @JsonProperty("created_at") val createdAt: OffsetDateTime,
  @JsonProperty("volume") val volume: Double? = null,
  @JsonProperty("remaining_volume") val remainingVolume: Double,
  @JsonProperty("reserved_fee") val reservedFee: Double,
  @JsonProperty("remaining_fee") val remainingFee: Double,
  @JsonProperty("paid_fee") val paidFee: Double,
  @JsonProperty("locked") val locked: Double,
  @JsonProperty("executed_volume") val executedVolume: Double,
  @JsonProperty("trades_count") val tradesCount: Int,
  @JsonProperty("trades") val trades: List<OrderTrade>
) {
  fun isFinished() = this.state == "done"

  fun getTotalVolume(): Double {
    return this.executedVolume + this.remainingVolume
  }
}

/**
 * 체결 데이터를 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property market     마켓의 유일 키
 * @property uuid       체결의 고유 아이디
 * @property price      체결 가격
 * @property volume     체결 양
 * @property funds      체결된 총 가격
 * @property side       체결 종류
 * @property createdAt  체결 시각
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderTrade(
  @JsonProperty("market") val market: String,
  @JsonDeserialize(using = UuidDeserializer::class)
  @JsonProperty("uuid") val uuid: UUID,
  @JsonProperty("price") val price: Double,
  @JsonProperty("volume") val volume: Double,
  @JsonProperty("funds") val funds: Double,
  @JsonProperty("side") val side: String,
  @JsonDeserialize(using = OffsetDateTimeDeserializer::class)
  @JsonProperty("created_at") val createdAt: OffsetDateTime
)