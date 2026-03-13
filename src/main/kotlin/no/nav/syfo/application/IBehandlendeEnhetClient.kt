package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.kartleggingssporsmal.infrastructure.clients.behandlendeenhet.BehandlendeEnhetResponseDTO

interface IBehandlendeEnhetClient {
    suspend fun getEnhet(
        personident: Personident,
    ): BehandlendeEnhetResponseDTO?
}
