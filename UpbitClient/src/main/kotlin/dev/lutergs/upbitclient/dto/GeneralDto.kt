package dev.lutergs.upbitclient.dto

import dev.lutergs.upbitclient.api.Param


data class MarketCode(
  val base: String,
  val quote: String
) : Param {
  override fun toParameterString(): String {
    return this.toJwtTokenString()
  }

  override fun toJwtTokenString(): String {
    return "market=$base-$quote"
  }

  override fun toString(): String {
    return "$base-$quote"
  }
}


enum class OrderState {
  WAIT,
  WATCH,
  DONE,
  CANCEL,
  @Deprecated("공식 문서의 파라미터 사용법을 발견하지 못함")
  WAIT_ALL, // 미체결 주문 전부 (wait, watch)
  @Deprecated("공식 문서의 파라미터 사용법을 발견하지 못함")
  DONE_ALL  // 체결 주문 전부 (done, cancel)
  ;

  fun toParameterString(): String {
    return when (this) {
      WAIT -> "state=wait"
      WATCH -> "state=watch"
      DONE -> "state=done"
      CANCEL -> "state=cancel"
      WAIT_ALL -> "states=wait,watch"
      DONE_ALL -> "states=done,cancel"
    }
  }

  fun toJwtTokenString(): String {
    return when (this) {
      WAIT -> "state=wait"
      WATCH -> "state=watch"
      DONE -> "state=done"
      CANCEL -> "state=cancel"
      WAIT_ALL -> "states[]=wait&states[]=watch"
      DONE_ALL -> "states[]=done&states[]=cancel"
    }
  }
}

enum class Ordering {
  ASC, DESC
}

enum class OrderSide {
  ASK,    // 매도
  BID;    // 매수

  companion object {
    fun fromRawString(s: String): OrderSide {
      return when (s) {
        "ask" -> ASK
        "bid" -> BID
        else -> throw IllegalStateException("OrderSide 파싱 시, 잘못된 값이 들어왔습니다. $s")
      }
    }
  }
}

enum class OrderType {
  LIMIT, PRICE, MARKET;

  companion object {
    fun fromString(s: String): OrderType {
      return when (s) {
        "price" -> PRICE
        "limit" -> LIMIT
        "market" -> MARKET
        else -> throw IllegalStateException("OrderType 파싱 시, 잘못된 값이 들어왔습니다. $s")
      }
    }
  }
}

data class Markets(
  val markets: List<MarketCode>
) : Param {
  override fun toParameterString(): String {
    return "markets=${markets.joinToString()}"
  }

  override fun toJwtTokenString(): String {
    return markets.joinToString(separator = "&") { "markets[]=$it" }
  }

  companion object {
    fun fromMarket(market: MarketCode): Markets {
      return Markets(listOf(market))
    }
  }
}