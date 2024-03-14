import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.apache.tools.ant.taskdefs.condition.Os

group = "no.nav.syfo"
version = "0.0.1"

object Versions {
    const val confluent = "7.5.1"
    const val flyway = "9.22.3"
    const val hikari = "5.1.0"
    const val isdialogmoteSchema = "1.0.5"
    const val jacksonDataType = "2.16.1"
    const val jetty = "9.4.54.v20240208"
    const val kafka = "3.6.1"
    const val kluent = "1.73"
    const val ktor = "2.3.8"
    const val logback = "1.4.14"
    const val logstashEncoder = "7.4"
    const val micrometerRegistry = "1.12.2"
    const val nimbusJoseJwt = "9.37.3"
    const val mockk = "1.13.9"
    const val postgres = "42.7.2"
    val postgresEmbedded = if (Os.isFamily(Os.FAMILY_MAC)) "1.0.0" else "0.13.4"
    const val scala = "2.13.12"
    const val spek = "2.0.19"
}
plugins {
    kotlin("jvm") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.0"
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
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:${Versions.jacksonDataType}")

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
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("org.apache.kafka:kafka_2.13:${Versions.kafka} -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.8.3")
            }
        }
        implementation("org.scala-lang:scala-library") {
            because("org.apache.kafka:kafka_2.13:${Versions.kafka} -> https://www.cve.org/CVERecord?id=CVE-2022-36944")
            version {
                require(Versions.scala)
            }
        }
    }
    implementation("io.confluent:kafka-avro-serializer:${Versions.confluent}", excludeLog4j)
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.11.3")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.26.0")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:${Versions.confluent}", excludeLog4j)
    constraints {
        implementation("org.json:json") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-5072")
            version {
                require("20231013")
            }
        }
        implementation("org.eclipse.jetty:jetty-server") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty:jetty-xml") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty:jetty-servlets") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("io.confluent:kafka-schema-registry:${Versions.confluent} -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(Versions.jetty)
            }
        }
    }
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:${Versions.isdialogmoteSchema}")

    testImplementation("com.nimbusds:nimbus-jose-jwt:${Versions.nimbusJoseJwt}")
    testImplementation("io.ktor:ktor-server-tests:${Versions.ktor}")
    testImplementation("io.ktor:ktor-client-mock:${Versions.ktor}")
    testImplementation("io.mockk:mockk:${Versions.mockk}")
    testImplementation("org.amshove.kluent:kluent:${Versions.kluent}")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:${Versions.spek}")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:${Versions.spek}")
}

kotlin {
    jvmToolchain(17)
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
