package dev.lutergs.santa.trade.worker

import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random


class ReactorTest {

  @Test
  fun repeatTest() {
    val test = AtomicInteger(0)

    Mono.defer { Mono.fromCallable {
      test.addAndGet(1)
      Random.nextInt()
        .also { println("test : $it") }
    } }
      .delayElement(Duration.ofMillis(200))
      .repeat {
        println("repeat: ${test.get()}")
        test.get() < 10
      }
      .last()
      .block()
      .let { println("result : Test: ${test.get()}, value : $it") }
  }
}