import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "0.0.1"

object Versions {
    const val confluent = "7.1.0"
    const val flyway = "8.5.9"
    const val hikari = "5.0.1"
    const val isdialogmoteSchema = "1.0.5"
    const val jackson = "2.13.1"
    const val kafka = "3.1.0"
    const val kafkaEmbedded = "3.1.0"
    const val kluent = "1.68"
    const val ktor = "2.0.0"
    const val logback = "1.2.11"
    const val logstashEncoder = "7.1.1"
    const val micrometerRegistry = "1.8.5"
    const val nimbusJoseJwt = "9.22"
    const val mockk = "1.12.3"
    const val postgres = "42.3.4"
    const val postgresEmbedded = "0.13.4"
    const val spek = "2.0.18"
}
plugins {
    kotlin("jvm") version "1.6.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
}

val githubUser: String by project
val githubPassword: String by project
repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://maven.pkg.github.com/navikt/isdialogmote-schema")
        credentials {
            username = githubUser
            password = githubPassword
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-serialization-jackson:${Versions.ktor}")
    implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
    implementation("io.ktor:ktor-client-apache:${Versions.ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-server-auth-jwt:${Versions.ktor}")
    implementation("io.ktor:ktor-server-call-id:${Versions.ktor}")
    implementation("io.ktor:ktor-server-content-negotiation:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation("io.ktor:ktor-server-status-pages:${Versions.ktor}")

    // Logging
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:${Versions.ktor}")
    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jackson}")

    // Database
    implementation("org.postgresql:postgresql:${Versions.postgres}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.postgresEmbedded}")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:${Versions.kafka}", excludeLog4j)
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}", excludeLog4j)
    implementation("io.confluent:kafka-schema-registry:${Versions.confluent}", excludeLog4j)
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema}")
    testImplementation("no.nav:kafka-embedded-env:${Versions.kafkaEmbedded}", excludeLog4j)

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusJoseJwt}")
    testImplementation("io.ktor:ktor-server-tests:${Versions.ktor}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}")
}

tasks {
    withType<Jar> {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "11"
    }

    withType<ShadowJar> {
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    withType<Test> {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
