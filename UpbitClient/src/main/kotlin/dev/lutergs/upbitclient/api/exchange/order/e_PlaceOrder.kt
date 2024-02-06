package dev.lutergs.upbitclient.api.exchange.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.dto.*
import java.time.OffsetDateTime
import java.util.UUID


// data class of POST https://api.upbit.com/v1/orders response

/**
 * 주문 정보를 나타내는 데이터 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property uuid                  주문의 고유 아이디
 * @property side                  주문 종류
 * @property ordType               주문 방식
 * @property price                 주문 당시 화폐 가격
 * @property state                 주문 상태
 * @property market                마켓의 유일키
 * @property createdAt             주문 생성 시간
 * @property volume                사용자가 입력한 주문 양
 * @property remainingVolume       체결 후 남은 주문 양
 * @property reservedFee           수수료로 예약된 비용
 * @property remainingFee          남은 수수료
 * @property paidFee               사용된 수수료
 * @property locked                거래에 사용중인 비용
 * @property executedVolume        체결된 양
 * @property tradesCount           해당 주문에 걸린 체결 수
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaceOrderResponse(
  @JsonDeserialize(using = UuidDeserializer::class)
  @JsonProperty("uuid") val uuid: UUID,
  @JsonProperty("side") val side: String,
  @JsonDeserialize(using = OrderTypeDeserializer::class)
  @JsonProperty("ord_type") val orderType: OrderType,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("price") val price: Double? = null,
  @JsonProperty("state") val state: String,
  @JsonDeserialize(using = MarketCodeDeserializer::class)
  @JsonProperty("market") val market: MarketCode,
  @JsonDeserialize(using = OffsetDateTimeDeserializer::class)
  @JsonProperty("created_at") val createdAt: OffsetDateTime,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("volume") val volume: Double? = null,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("remaining_volume") val remainingVolume: Double? = null,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("reserved_fee") val reservedFee: Double,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("remaining_fee") val remainingFee: Double,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("paid_fee") val paidFee: Double,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("locked") val locked: Double,
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("executed_volume") val executedVolume: Double? = null,
  @JsonProperty("trades_count") val tradesCount: Int
)