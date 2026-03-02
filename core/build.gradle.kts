plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.spring") version "2.1.21"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.1.21"
}

group = "riven"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Workflow Execution
    implementation("io.temporal:temporal-kotlin:1.32.1")
    implementation("io.temporal:temporal-sdk:1.24.1")
    implementation("io.temporal:temporal-spring-boot-starter:1.31.0")

    // Distributed Locking (ShedLock)
    implementation("net.javacrumbs.shedlock:shedlock-spring:7.5.0")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc-template:7.5.0")

    // Security/JWT
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server:3.5.0")
    implementation("org.springframework.security:spring-security-oauth2-jose:6.5.0")

    // Supabase
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.github.jan-tennert.supabase:serializer-jackson:3.1.4")

    // Pdf Generation
    implementation("com.github.librepdf:openpdf:1.3.30")

    // Swagger/OpenAPI
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // PostHog Analytics
    implementation("com.posthog:posthog-server:2.3.2")

    // Resilience4j Circuit Breaker
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.3.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Object Mapping
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Postgres/JPA
    implementation("io.hypersistence:hypersistence-utils-hibernate-63:3.9.2")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway Database Migrations
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Schema Validation
    implementation("com.networknt:json-schema-validator:1.0.83")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    // Testing
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:3.2.0")
    testImplementation("com.nimbusds:nimbus-jose-jwt:9.37.2")
    testImplementation("com.h2database:h2")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.temporal:temporal-testing:1.24.1")
    testImplementation("org.testcontainers:testcontainers:2.0.3")
    testImplementation("org.testcontainers:testcontainers-postgresql:2.0.3")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
