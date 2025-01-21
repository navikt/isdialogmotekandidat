group = "no.nav.syfo"
version = "0.0.1"

val confluentVersion = "7.8.0"
val flywayVersion = "11.1.1"
val hikariVersion = "6.2.1"
val isdialogmoteSchemaVersion = "1.0.5"
val jacksonDataTypeVersion = "2.18.2"
val jettyVersion = "9.4.57.v20241219"
val kafkaVersion = "3.9.0"
val kluentVersion = "1.73"
val ktorVersion = "3.0.3"
val logbackVersion = "1.5.16"
val logstashEncoderVersion = "8.0"
val micrometerRegistryVersion = "1.12.13"
val nimbusJoseJwtVersion = "10.0.1"
val mockkVersion = "1.13.16"
val postgresVersion = "42.7.4"
val postgresEmbeddedVersion = "2.0.7"
val spekVersion = "2.0.19"

plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

repositories {
    mavenCentral()
    maven(url = "https://packages.confluent.io/maven/")
    maven(url = "https://jitpack.io")
    maven {
        url = uri("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))

    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-call-id:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstashEncoderVersion")

    // Metrics and Prometheus
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometerRegistryVersion")

    // (De-)serialization
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonDataTypeVersion")

    // Database
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")
    testImplementation("io.zonky.test:embedded-postgres:$postgresEmbeddedVersion")

    // Kafka
    val excludeLog4j = fun ExternalModuleDependency.() {
        exclude(group = "log4j")
    }
    implementation("org.apache.kafka:kafka_2.13:$kafkaVersion", excludeLog4j)
    constraints {
        implementation("org.apache.zookeeper:zookeeper") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://www.cve.org/CVERecord?id=CVE-2023-44981")
            version {
                require("3.9.3")
            }
        }
        implementation("org.bitbucket.b_c:jose4j") {
            because("org.apache.kafka:kafka_2.13:$kafkaVersion -> https://github.com/advisories/GHSA-6qvw-249j-h44c")
            version {
                require("0.9.6")
            }
        }
    }
    implementation("io.confluent:kafka-avro-serializer:$confluentVersion", excludeLog4j)
    implementation("no.nav.syfo.dialogmote.avro:isdialogmote-schema:$isdialogmoteSchemaVersion")
    constraints {
        implementation("org.apache.avro:avro") {
            because("io.confluent:kafka-avro-serializer:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-39410")
            version {
                require("1.11.4")
            }
        }
        implementation("org.apache.commons:commons-compress") {
            because("org.apache.commons:commons-compress:1.22 -> https://www.cve.org/CVERecord?id=CVE-2012-2098")
            version {
                require("1.27.1")
            }
        }
    }
    implementation("io.confluent:kafka-schema-registry:$confluentVersion", excludeLog4j)
    constraints {
        implementation("io.github.classgraph:classgraph") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2021-47621")
            version {
                require("4.8.179")
            }
        }
        implementation("org.json:json") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-5072")
            version {
                require("20240303")
            }
        }
        implementation("org.eclipse.jetty:jetty-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-xml") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty:jetty-servlets") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
        implementation("org.eclipse.jetty.http2:http2-server") {
            because("io.confluent:kafka-schema-registry:$confluentVersion -> https://www.cve.org/CVERecord?id=CVE-2023-36478")
            version {
                require(jettyVersion)
            }
        }
    }
    testImplementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.amshove.kluent:kluent:$kluentVersion")
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:$spekVersion")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:$spekVersion")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    jar {
        manifest.attributes["Main-Class"] = "no.nav.syfo.AppKt"
    }

    create("printVersion") {
        doLast {
            println(project.version)
        }
    }

    shadowJar {
        mergeServiceFiles()
        archiveBaseName.set("app")
        archiveClassifier.set("")
        archiveVersion.set("")
    }

    test {
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging.showStandardStreams = true
    }
}
