package dev.lutergs.santa.trade.worker

import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class Test {

  @Test
  fun test() {
    val logger = LoggerFactory.getLogger("testL")
    logger.info("tets!!")
    logger.error("Test!!")
  }
}