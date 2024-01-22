package dev.lutergs.upbitclient.api.quotation.market

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.lutergs.upbitclient.api.Param
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.dto.MarketCodeDeserializer
import dev.lutergs.upbitclient.webclient.Requester
import reactor.core.publisher.Flux

class MarketRequester(requester: Requester) : RequestDao(requester) {

    fun getMarketCode(): Flux<MarketCodeResponse> {
        return this.requester.getMany("/market/all", MarketCodeRequest(true), MarketCodeResponse::class)
    }
}

data class MarketCodeRequest(
    @JsonProperty("isDetails")  val isDetails: Boolean
): Param {
    override fun toParameterString(): String {
        return this.toJwtTokenString()
    }

    override fun toJwtTokenString(): String {
        return "isDetails=${this.isDetails}"
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MarketCodeResponse(
  @JsonDeserialize(using = MarketCodeDeserializer::class)
    @JsonProperty("market")         val market: MarketCode,
  @JsonProperty("korean_name")    val koreanName: String,
  @JsonProperty("english_name")   val englishName: String,
  @JsonProperty("market_warning") val marketWarning: MarketWarning
)

enum class MarketWarning {
    NONE, CAUTION
}