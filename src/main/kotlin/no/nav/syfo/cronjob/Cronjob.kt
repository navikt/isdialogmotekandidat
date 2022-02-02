package no.nav.syfo.cronjob

interface Cronjob {
    suspend fun run()
    val initialDelayMinutes: Long
    val intervalDelayMinutes: Long
}
