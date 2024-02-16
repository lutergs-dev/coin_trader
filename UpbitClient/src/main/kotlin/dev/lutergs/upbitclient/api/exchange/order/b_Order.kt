package dev.lutergs.upbitclient.api.exchange.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import dev.lutergs.upbitclient.dto.*
import java.math.BigDecimal
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
  @JsonProperty("uuid") val uuid: UUID,
  @JsonProperty("side") val side: OrderSide,
  @JsonProperty("ord_type") val orderType: OrderType,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("price") val price: BigDecimal? = null,
  @JsonProperty("state") val state: String,
  @JsonProperty("market") val market: MarketCode,
  @JsonProperty("created_at") val createdAt: OffsetDateTime,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("volume") val volume: BigDecimal? = null,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("remaining_volume") val remainingVolume: BigDecimal? = null,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("reserved_fee") val reservedFee: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("remaining_fee") val remainingFee: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("paid_fee") val paidFee: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("locked") val locked: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("executed_volume") val executedVolume: BigDecimal,
  @JsonProperty("trades_count") val tradesCount: Int,
  @JsonProperty("trades") val trades: List<OrderTrade>
) {

  /**
   * 주문의 상태가 완료될 때, true 아닐 때 false 를 return 함.
   * 시장가 매수의 경우 문서에 따라 체결주문 취소 (cancel) 거나 (매수금액이 극히 일부 남았을 때), 완료 (done) 모두를 주문완료라고 판단함.
   * 그 이외의 경우는 모두 "done" 일 때 완료라고 판단
   * */
  fun isFinished(): Boolean {
    return when (this.orderType) {
      OrderType.PRICE -> this.state == "cancel" || this.state == "done"
      OrderType.MARKET -> this.state == "done"
      OrderType.LIMIT -> this.state == "done"
    }
  }

  fun totalVolume(): BigDecimal  {
    return when (this.orderType) {
      OrderType.PRICE -> run {
        takeIf { this.isFinished() } ?: throw IllegalStateException("시장가 매수 주문이 완료되지 않은 상태에서 주문량을 조회했습니다.")
        this.trades.sumOf { it.volume }
      }
      OrderType.MARKET -> this.volume!!
      OrderType.LIMIT -> this.volume!!
    }.stripTrailingZeros()
  }


  fun avgPrice(): BigDecimal {
    return when (this.orderType) {
      OrderType.PRICE -> run {
        takeIf { this.isFinished() } ?: throw IllegalStateException("시장가 매수 주문이 완료되지 않은 상태에서, 평균매수단가를 조회했습니다.")
        (this.trades.sumOf { it.price * it.volume }) / (this.trades.sumOf { it.volume })
      }
      OrderType.MARKET -> run {
        takeIf { this.isFinished() } ?: throw IllegalStateException("시장가 매도 주문이 완료되지 않은 상태에서, 평균매도단가를 조회했습니다.")
        (this.trades.sumOf { it.price * it.volume }) / (this.trades.sumOf { it.volume })
      }
      OrderType.LIMIT -> this.price!!
    }.stripTrailingZeros()
  }

  fun totalPrice(): BigDecimal {
    return (this.totalVolume() * this.avgPrice()).stripTrailingZeros()
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
  @JsonProperty("uuid") val uuid: UUID,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("price") val price: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("volume") val volume: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("funds") val funds: BigDecimal,
  @JsonProperty("side") val side: String,
  @JsonProperty("created_at") val createdAt: OffsetDateTime
)