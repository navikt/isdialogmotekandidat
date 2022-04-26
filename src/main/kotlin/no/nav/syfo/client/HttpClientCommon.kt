package no.nav.syfo.client

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.nav.syfo.util.*
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

fun httpClientDefault() = HttpClient(CIO) {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    expectSuccess = true
}

val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
    install(ContentNegotiation) {
        jackson { configure() }
    }
    expectSuccess = true
    engine {
        customizeClient {
            setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
        }
    }
}

fun httpClientProxy() = HttpClient(
    engineFactory = Apache,
    block = proxyConfig,
)
