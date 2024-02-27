package dev.lutergs.santa.trade.manager.domain.entity

import dev.lutergs.upbitclient.api.quotation.candle.CandleMinuteResponse
import dev.lutergs.upbitclient.api.quotation.candle.getHighPriceCandle
import dev.lutergs.upbitclient.api.quotation.candle.getLowPriceCandle
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderBookResponse
import dev.lutergs.upbitclient.api.quotation.ticker.TickerResponse
import org.slf4j.LoggerFactory
import java.math.BigDecimal

class Priority(
  private val ticker: TickerResponse,
  private val candles: List<CandleMinuteResponse>,
  private val orderbook: OrderBookResponse
): Comparable<Priority> {
  val coin = ticker.market
  private val high = candles.getHighPriceCandle()
  private val low = candles.getLowPriceCandle()

  /**
   * BID ASK (bid 가 사는거, ask 가 파는거)
   * 호가를 기준으로, 1호가부터 가중치를 둬서 비교 판단
   * 가중치는 n호가 (총구매가격 - 총판매가격) 의 ( (15-n) / 15 ) 를 반영함. 즉, 1호가에 가까운 가격일수록 크게 반영
   *
   * (2023.01.26) 근데 이건 너무 고정된 결과를 낳을 수도 있다. 일단 이걸 방지하고자 거래량 자체를 판단하는 것으로 수정
   * (2023.01.28) 손실이 너무 커서, 원래 버전으로 rollback
   * */
  val sellBuyPriceWeight: BigDecimal get(){
    val ordBookLen = orderbook.orderbookUnits.size
    return orderbook.orderbookUnits
      .mapIndexed { idx, data -> (data.bidSize * data.bidPrice - data.askSize * data.askPrice) * ((BigDecimal(ordBookLen) - BigDecimal(idx)) / BigDecimal(ordBookLen)) }
      .let {
        // TODO : 왜 empty 로 들어오는지에 대한 검증 필요
        if (it.isEmpty()) BigDecimal.ZERO
        else it.reduce {a, b -> a + b}
      }
  }

  val isGoUp: Boolean = takeIf { this.high.timestamp < this.low.timestamp }
    ?.let { candles
      .indexOf(candles.find { it.lowPrice == low.lowPrice } ?: throw IllegalStateException("최소값에 해당하는 인덱스를 찾을 수 없습니다."))
      .let { lowIdx -> candles
        .subList(lowIdx, candles.size)
        .any { it.lowPrice > ticker.tradePrice }
      }
    } ?: false


  /**
   * 최근부터 6시간 동안의 데이터를 확인해, 다음과 같은 기준을 따름.
   *
   * 1. (고가 - 현재가) : (현재가 - 저가) 의 비율이 9:1 이상이면서 고가가 저가보다 먼저 일어난 경우
   * 3. 저가와 현재가가 동일할 경우 거래하지 않음.
   * 4. 현재가가 저가보다 높을 때, (고가 - 저가) : (현재가 - 저가) = 100 : 3 이하인 경우는 거래하지 않음.
   * 5. RSI 지표가 20이하 80 이상인 것은 거래하지 않음.
   * */
  val isTradeable: Boolean = sequenceOf (
    Pair("(고가 - 현재가) : (현재가 - 저가) 의 비율이 9:1 이상이면서 고가가 저가보다 먼저 일어난 경우") {
      ((high.highPrice - ticker.tradePrice).abs() >= (low.lowPrice - ticker.tradePrice).abs() * BigDecimal(8)) &&
        (high.timestamp < low.timestamp)
    },
    Pair("저가와 현재가가 동일") {
      low.lowPrice >= ticker.tradePrice
    },
    Pair("RSI 지표가 20이하 80 이상") {
      val rsi = CandleMinuteResponse.rsi(candles)
      rsi >= BigDecimal(80.0) || rsi <= BigDecimal(20.0)
    }
  ).firstOrNull { it.second() }
    ?.let {
      logger.info("코인 ${ticker.market.quote} 는 ${it.first} 이기 때문에 거래하지 않습니다.")
      false
    } ?: true

  override fun compareTo(other: Priority): Int {
    val compareWeight: () -> Int = { when {
      this.sellBuyPriceWeight > other.sellBuyPriceWeight -> 1
      this.sellBuyPriceWeight < other.sellBuyPriceWeight -> -1
      else -> 0
    } }
    return when {
      this.isGoUp && other.isGoUp -> compareWeight()
      this.isGoUp && !other.isGoUp -> 1
      !this.isGoUp && other.isGoUp -> -1
      !this.isGoUp && !other.isGoUp -> compareWeight()
      else -> throw IllegalStateException("존재하지 않는 대소비교입니다")
    }
  }

  companion object {
    private val logger = LoggerFactory.getLogger(Priority::class.java)
  }
}
