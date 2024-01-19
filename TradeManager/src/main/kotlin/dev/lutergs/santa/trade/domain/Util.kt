package dev.lutergs.santa.trade.domain

import kotlin.random.Random


object Util {
  fun generateRandomString(length: Int = 10): String {
    val charPool: List<Char> = ('A'..'Z') + ('a'..'z') + ('0'..'9')

    return (1..length)
      .map { Random.nextInt(0, charPool.size) }
      .map(charPool::get)
      .joinToString("")
  }
}

