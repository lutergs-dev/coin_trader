import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.2.2"
  id("io.spring.dependency-management") version "1.1.4"
  id("org.graalvm.buildtools.native") version "0.9.28"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
}

group = "dev.lutergs"
version = "0.0.30"

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  implementation(project(":UpbitClient"))
  implementation(project(":UniversalUtilAndDao"))

  implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.2")
  implementation("org.springframework.kafka:spring-kafka:3.0.10")

  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

  implementation("com.google.code.gson:gson:2.10.1")

  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")

  testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
  testImplementation("io.projectreactor:reactor-test:3.5.4")
  testImplementation("org.springframework.kafka:spring-kafka-test:3.0.10")
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
