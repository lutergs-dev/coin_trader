import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "3.2.1"
  id("io.spring.dependency-management") version "1.1.4"
  id("org.graalvm.buildtools.native") version "0.9.28"
  kotlin("jvm") version "1.9.21"
  kotlin("plugin.spring") version "1.9.21"
}

group = "dev.lutergs"
version = "0.0.10"

java {
  sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.1")
//    implementation("io.projectreactor.netty:reactor-netty-http:1.1.15")
  implementation("io.projectreactor:reactor-core:3.6.2")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")

  implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

  implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.10")

  implementation("com.auth0:java-jwt:4.4.0")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
  testImplementation("io.projectreactor:reactor-test:3.5.4")
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