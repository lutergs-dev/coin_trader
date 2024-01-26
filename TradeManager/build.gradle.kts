import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
}

group = "dev.lutergs"
version = "0.0.20"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":UpbitClient"))

    implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.1")
    implementation("org.springframework.kafka:spring-kafka:3.0.10")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive:3.0.4")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc:3.0.4")

    implementation("com.oracle.database.r2dbc:oracle-r2dbc:1.2.0")
    implementation("io.r2dbc:r2dbc-pool:1.0.1.RELEASE")
    implementation("io.r2dbc:r2dbc-spi:1.0.0.RELEASE")
    implementation("com.oracle.database.jdbc:ojdbc11:21.11.0.0")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.1")

    implementation("io.kubernetes:client-java:19.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test:3.1.0")
    testImplementation("io.projectreactor:reactor-test:3.5.4")
    testImplementation("org.springframework.kafka:spring-kafka-test:3.0.4")
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
