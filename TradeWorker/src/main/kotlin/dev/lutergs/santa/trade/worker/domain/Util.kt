package dev.lutergs.santa.trade.worker.domain

fun Double.toStrWithPoint(point: Int = 2): String {
  return String.format("%.${point}f", this)
}