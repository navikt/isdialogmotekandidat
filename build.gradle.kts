import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "no.nav.syfo"
version = "0.0.1"

object Versions {
    const val ktor = "1.6.5"
    const val kotlin = "1.5.31"
    const val logback = "1.2.6"
    const val logstashEncoder = "6.6"
    const val hikari = "5.0.0"
    const val postgres = "42.3.1"
    const val flyway = "8.0.2"
    const val kluent = "1.68"
    const val spek = "2.0.17"
    const val pgEmbedded = "0.13.4"
    const val micrometerRegistry = "1.7.5"
}
plugins {
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.ktor:ktor-server-core:${Versions.ktor}")
    implementation("io.ktor:ktor-jackson:${Versions.ktor}")
    implementation("io.ktor:ktor-server-netty:${Versions.ktor}")
    implementation("io.ktor:ktor-metrics-micrometer:${Versions.ktor}")

    implementation("io.micrometer:micrometer-registry-prometheus:${Versions.micrometerRegistry}")

    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("org.postgresql:postgresql:${Versions.postgres}")
    implementation("com.zaxxer:HikariCP:${Versions.hikari}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("net.logstash.logback:logstash-logback-encoder:${Versions.logstashEncoder}")

    testImplementation("io.ktor:ktor-server-tests:${Versions.ktor}")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Versions.kotlin}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("com.opentable.components:otj-pg-embedded:${Versions.pgEmbedded}")

    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}")
    testRuntimeOnly(kotlin("reflect"))
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
