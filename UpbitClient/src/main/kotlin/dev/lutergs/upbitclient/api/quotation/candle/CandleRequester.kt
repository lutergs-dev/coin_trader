package dev.lutergs.upbitclient.api.quotation.candle

import dev.lutergs.upbitclient.api.Param
import dev.lutergs.upbitclient.api.RequestDao
import dev.lutergs.upbitclient.dto.MarketCode
import dev.lutergs.upbitclient.webclient.Requester
import reactor.core.publisher.Flux

class CandleRequester(requester: Requester) : RequestDao(requester) {

  fun getMinute(request: CandleMinuteRequest): Flux<CandleMinuteResponse> {
    return this.requester.getMany("/candles/minutes/${request.unit}", request, CandleMinuteResponse::class)
  }
}

data class CandleMinuteRequest(
  val market: MarketCode,
  val count: Int,
  val unit: Int
  // TODO : to 변수 있음, 현재는 최신값만 가져오기 때문에 포함하지 않음
) : Param {

  init {
    if (!(listOf(1, 3, 5, 15, 10, 30, 60, 240).contains(this.unit))) {
      throw IllegalArgumentException("분 캔들 단위는 다음 값 중 하나여야 합니다. : 1, 3, 5, 15, 10, 30, 60, 240")
    }
  }

  override fun toParameterString(): String {
    return "${market.toParameterString()}&count=$count"
  }

  override fun toJwtTokenString(): String {
    return "${market.toJwtTokenString()}&count=$count"
  }
}