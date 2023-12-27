package dev.lutergs.upbeatclient.webclient

import dev.lutergs.upbeatclient.api.exchange.account.AccountRequester
import dev.lutergs.upbeatclient.api.exchange.order.OrderRequester
import dev.lutergs.upbeatclient.api.quotation.market.MarketRequester
import dev.lutergs.upbeatclient.api.quotation.ticker.TickerRequester

class Client(
    accessKey: String,
    secretKey: String
) {
    private val requester = Requester(
        "https://api.upbit.com/v1",
        TokenGenerator(accessKey, secretKey)
    )
    val account = AccountRequester(this.requester)
    val market = MarketRequester(this.requester)
    val ticker = TickerRequester(this.requester)
    val order = OrderRequester(this.requester)
}