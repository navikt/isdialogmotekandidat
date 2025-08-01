package no.nav.syfo.infrastructure.clients.pdl.domain

data class PdlIdenterResponse(
    val data: PdlHentIdenter?,
    val errors: List<PdlError>?,
)

data class PdlHentIdenter(
    val hentIdenter: PdlIdenter?,
)

data class PdlIdenter(
    val identer: List<PdlIdent>,
) {
    val aktivIdent: String? = identer.firstOrNull {
        it.gruppe == IdentType.FOLKEREGISTERIDENT && !it.historisk
    }?.ident

    fun identhendelseIsNotHistorisk(newIdent: String): Boolean {
        return identer.none { it.ident == newIdent && it.historisk }
    }
}

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: IdentType,
)

enum class IdentType {
    FOLKEREGISTERIDENT,
    AKTORID,
    NPID,
}
