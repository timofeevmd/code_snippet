import jp.co.soramitsu.iroha2.*
import jp.co.soramitsu.iroha2.generated.datamodel.account.AccountId
import jp.co.soramitsu.iroha2.generated.datamodel.predicate.GenericValuePredicateBox
import jp.co.soramitsu.iroha2.generated.datamodel.predicate.value.ValuePredicate
import jp.co.soramitsu.iroha2.query.QueryBuilder
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.security.KeyPair

fun main(args: Array<String>): Unit = runBlocking{
    val peerUrl = "http://127.0.0.1:8080"
    val telemetryUrl = "http://127.0.0.1:8180"
    val admin = AccountId("bob".asName(), "wonderland".asDomainId()) // transactions send behalf of this account
    val adminKeyPair = keyPairFromHex("7233bfc89dcbd68c19fde6ce6158225298ec1131b6a130d1aeb454c1ab5183c0",
                                      "9ac47abf59b356e0bd7dcbbbb4dec080e302156a48ca907e47cb6aea1d32719e")

    val query = Query(peerUrl, telemetryUrl, admin, adminKeyPair)

    query.findAllDomains()
        .also { println("ALL DOMAINS: ${it.map { d -> d.id.asString() }}") }

}

open class Query (peerUrl: String,
                    telemetryUrl: String,
                    private val admin: AccountId,
                    private val keyPair: KeyPair) {

    private val client = AdminIroha2Client(URL(peerUrl), URL(telemetryUrl), log = true)

    suspend fun findAllDomains(queryFilter: GenericValuePredicateBox<ValuePredicate>? = null) = QueryBuilder
        .findAllDomains(queryFilter)
        .account(admin)
        .buildSigned(keyPair)
        .let { client.sendQuery(it) }
}