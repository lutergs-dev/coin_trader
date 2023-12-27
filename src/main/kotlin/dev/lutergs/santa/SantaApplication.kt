package dev.lutergs.santa

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SantaApplication

fun main(args: Array<String>) {
    runApplication<SantaApplication>(*args)
}
