package no.nav.syfo.application

import java.sql.Connection

interface ITransactionManager {
    suspend fun <T> run(block: suspend (transaction: ITransaction) -> T): T
}

interface ITransaction {
    val connection: Connection
}
