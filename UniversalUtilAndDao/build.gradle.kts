import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.2.2"
  id("io.spring.dependency-management") version "1.1.4"
  id("org.graalvm.buildtools.native") version "0.9.28"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
}

group = "dev.lutergs"
version = "0.0.2"

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  api("org.springframework.boot:spring-boot-starter-data-r2dbc:3.2.2")

  // trade result data store
  api("com.oracle.database.r2dbc:oracle-r2dbc:1.2.0")
  api("io.r2dbc:r2dbc-pool:1.0.1.RELEASE")
  api("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
  api("com.oracle.database.jdbc:ojdbc11:21.11.0.0")

  // coin time-base data store
  api("com.influxdb:influxdb3-java:0.5.1")

  // danger-coin data store
  api("org.springframework.boot:spring-boot-starter-data-mongodb-reactive:3.0.4")

  api("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

  api("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
  api("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
  api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")

  testApi("org.springframework.boot:spring-boot-starter-test:3.2.2")
  testApi("io.projectreactor:reactor-test:3.5.4")
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs += "-Xjsr305=strict"
    jvmTarget = "21"
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}
