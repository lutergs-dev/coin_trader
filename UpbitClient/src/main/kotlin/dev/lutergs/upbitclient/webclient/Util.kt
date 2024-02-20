package dev.lutergs.upbitclient.webclient

import java.time.LocalDateTime

fun LocalDateTime.nextSecond(): LocalDateTime = this.plusSeconds(1).minusNanos(this.nano.toLong())