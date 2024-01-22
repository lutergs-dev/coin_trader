package dev.lutergs.upbitclient.api.exchange.account

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.dto.NumberStringDeserializer
import dev.lutergs.upbitclient.webclient.Requester
import reactor.core.publisher.Flux

class AccountRequester(requester: Requester) : RequestDao(requester) {
    fun getAccount(): Flux<AccountResponse> {
        return this.requester.getMany("/accounts", null, AccountResponse::class)
    }
}

/**
 * @author LuterGS(lutergs@lutergs.dev)
 * @property currency               화폐를 의미하는 영문 대문자 코드
 * @property balance                주문가능 금액/수량
 * @property locked                 주문 중 묶여있는 금액/수량
 * @property avgBuyPrice            매수평균가
 * @property avgBuyPriceModified    매수평균가 수정 여부
 * @property unitCurrency           평단가 기준 화폐
 * */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountResponse(
    @JsonProperty("currency")               val currency: String,       // TODO : 추후 ENUM 으로 변경
    @JsonDeserialize(using = NumberStringDeserializer::class)
    @JsonProperty("balance")                val balance: Double,
    @JsonDeserialize(using = NumberStringDeserializer::class)
    @JsonProperty("locked")                 val locked: Double,
    @JsonDeserialize(using = NumberStringDeserializer::class)
    @JsonProperty("avg_buy_price")          val avgBuyPrice: Double,
    @JsonProperty("avg_buy_price_modified") val avgBuyPriceModified: Boolean,
    @JsonProperty("unit_currency")          val unitCurrency: String
)