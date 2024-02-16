package dev.lutergs.upbitclient.api.exchange.order

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import dev.lutergs.upbitclient.dto.*
import java.math.BigDecimal


// data class of GET https://api.upbit.com/v1/orders/chance response

/**
 * 마켓별 주문 가능 정보 응답 데이터를 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property bidFee                 매수 수수료 비율
 * @property askFee                 매도 수수료 비율
 * @property market                 마켓에 대한 정보
 * @property askTypes               매도 주문 지원 방식 (Array[String])
 * @property bidTypes               매수 주문 지원 방식 (Array[String])
 * @property bidAccount             매수 시 사용하는 화폐의 계좌 상태
 * @property askAccount             매도 시 사용하는 화폐의 계좌 상태
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderChanceResponse(
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("bid_fee") val bidFee: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("ask_fee") val askFee: BigDecimal,
  @JsonProperty("market") val market: OrderChanceMarket,
  @JsonProperty("ask_types") val askTypes: List<String> = listOf(),
  @JsonProperty("bid_types") val bidTypes: List<String> = listOf(),
  @JsonProperty("bid_account") val bidAccount: OrderChanceAccountStatus,
  @JsonProperty("ask_account") val askAccount: OrderChanceAccountStatus
)

/**
 * 마켓에 대한 정보를 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property id                     마켓의 유일 키
 * @property name                   마켓 이름
 * @property orderTypes             지원 주문 방식 (Array[String])
 * @property orderSides             지원 주문 종류 (Array[String])
 * @property bid                    매수 시 제약사항
 * @property ask                    매도 시 제약사항
 * @property maxTotal               최대 매도/매수 금액
 * @property state                  마켓 운영 상태
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderChanceMarket(
  @JsonProperty("id") val id: MarketCode,
  @JsonProperty("name") val name: String,
  @JsonProperty("order_types") val orderTypes: List<String>,
  @JsonProperty("order_sides") val orderSides: List<String>,
  @JsonProperty("bid") val bid: OrderChanceMarketRestriction,
  @JsonProperty("ask") val ask: OrderChanceMarketRestriction,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("max_total") val maxTotal: BigDecimal,
  @JsonProperty("state") val state: String
)

/**
 * 마켓 제약사항을 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property currency               화폐를 의미하는 영문 대문자 코드
 * @property priceUnit              주문금액 단위
 * @property minTotal               최소 매도/매수 금액
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
data class OrderChanceMarketRestriction(
  @JsonProperty("currency") val currency: String,
  @JsonProperty("price_unit") val priceUnit: String? = null,
  @JsonProperty("min_total") val minTotal: BigDecimal
)

/**
 * 계좌 상태를 나타내는 클래스
 *
 * @author LuterGS(lutergs@lutergs.dev)
 * @property currency               화폐를 의미하는 영문 대문자 코드
 * @property balance                주문가능 금액/수량
 * @property locked                 주문 중 묶여있는 금액/수량
 * @property avgBuyPrice            매수평균가
 * @property avgBuyPriceModified    매수평균가 수정 여부
 * @property unitCurrency           평단가 기준 화폐
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class OrderChanceAccountStatus(
  @JsonProperty("currency") val currency: String,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("balance") val balance: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("locked") val locked: BigDecimal,
  @JsonSerialize(using = NumberStringSerializer::class)
  @JsonDeserialize(using = NumberStringDeserializer::class)
  @JsonProperty("avg_buy_price") val avgBuyPrice: BigDecimal,
  @JsonProperty("avg_buy_price_modified") val avgBuyPriceModified: Boolean,
  @JsonProperty("unit_currency") val unitCurrency: String
)