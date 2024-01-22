package dev.lutergs.upbitclient.webclient

import dev.lutergs.upbitclient.api.exchange.account.AccountRequester
import dev.lutergs.upbitclient.api.exchange.order.OrderRequester
import dev.lutergs.upbitclient.api.quotation.candle.CandleRequester
import dev.lutergs.upbitclient.api.quotation.market.MarketRequester
import dev.lutergs.upbitclient.api.quotation.orderbook.OrderBookRequester
import dev.lutergs.upbitclient.api.quotation.ticker.TickerRequester

abstract class Client {
  abstract val account: AccountRequester
  abstract val market: MarketRequester
  abstract val ticker: TickerRequester
  abstract val order: OrderRequester
  abstract val orderBook: OrderBookRequester
  abstract val candle: CandleRequester
}

class BasicClient(
  accessKey: String,
  secretKey: String
): Client() {
  private val requester = Requester(
    "https://api.upbit.com/v1",
    TokenGenerator(accessKey, secretKey)
  )
  override val account = AccountRequester(this.requester)
  override val market = MarketRequester(this.requester)
  override val ticker = TickerRequester(this.requester)
  override val order = OrderRequester(this.requester)
  override val orderBook = OrderBookRequester(this.requester)
  override val candle = CandleRequester(this.requester)
}