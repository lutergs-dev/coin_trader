import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.2.2"
  id("io.spring.dependency-management") version "1.1.4"
//  id("org.graalvm.buildtools.native") version "0.9.28"
  id("org.jetbrains.kotlinx.dataframe") version "0.12.1"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
}

group = "dev.lutergs"
version = "0.1.7"

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  // other module implementation
  implementation(project(":UpbitClient"))
  implementation(project(":Util"))

  // Spring dependency
  implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.2")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive:3.2.1")
  implementation("org.springframework.kafka:spring-kafka:3.0.12")

  // kubernetes setting
  implementation("io.kubernetes:client-java:19.0.0")

  // Graph setting
  implementation("org.knowm.xchart:xchart:3.8.7")


  // Jackson JSON
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")

  // Kotlin and reactor
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.22")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")


  testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
  testImplementation("io.projectreactor:reactor-test:3.6.2")
  testImplementation("org.springframework.kafka:spring-kafka-test:3.0.12")
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
