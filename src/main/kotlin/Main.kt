import jp.co.soramitsu.iroha2.*
import jp.co.soramitsu.iroha2.generated.crypto.PublicKey
import jp.co.soramitsu.iroha2.generated.datamodel.Value
import jp.co.soramitsu.iroha2.generated.datamodel.account.AccountId
import jp.co.soramitsu.iroha2.generated.datamodel.metadata.Metadata
import jp.co.soramitsu.iroha2.generated.datamodel.name.Name
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.URL
import java.security.KeyPair

fun main(args: Array<String>): Unit = runBlocking{
    val peerUrl = "http://127.0.0.1:8080"
    val telemetryUrl = "http://127.0.0.1:8180"
    val admin = AccountId("bob".asName(), "wonderland".asDomainId()) // transactions send behalf of this account
    val adminKeyPair = keyPairFromHex("7233bfc89dcbd68c19fde6ce6158225298ec1131b6a130d1aeb454c1ab5183c0",
                                      "9ac47abf59b356e0bd7dcbbbb4dec080e302156a48ca907e47cb6aea1d32719e")

    val sendTransaction = SendTransaction(peerUrl, telemetryUrl, admin, adminKeyPair)

    val domain = "domain_${System.currentTimeMillis()}"
    sendTransaction.registerDomain(domain).also { println("DOMAIN $domain CREATED") }

    val joe = "joe_${System.currentTimeMillis()}$ACCOUNT_ID_DELIMITER$domain"
    val joeKeyPair = generateKeyPair()
    sendTransaction.registerAccount(joe, listOf(joeKeyPair.public.toIrohaPublicKey()))
        .also { println("ACCOUNT $joe CREATED") }

}

open class SendTransaction (peerUrl: String,
                    telemetryUrl: String,
                    private val admin: AccountId,
                    private val keyPair: KeyPair,
                    private val timeout: Long = 10000) {

    private val client = AdminIroha2Client(URL(peerUrl), URL(telemetryUrl), log = true)

    suspend fun registerDomain(
        id: String,
        metadata: Map<Name, Value> = mapOf(),
        admin: AccountId = this.admin,
        keyPair: KeyPair = this.keyPair
    ) {
        client.sendTransaction {
            account(admin)
            this.registerDomain(id.asDomainId(), metadata)
            buildSigned(keyPair)
        }.also {
            withTimeout(timeout) { it.await() }
        }
    }

    suspend fun registerAccount(
        id: String,
        signatories: List<PublicKey>,
        metadata: Map<Name, Value> = mapOf(),
        admin: AccountId = this.admin,
        keyPair: KeyPair = this.keyPair
    ) {
        client.sendTransaction {
            account(admin)
            this.registerAccount(id.asAccountId(), signatories, Metadata(metadata))
            buildSigned(keyPair)
        }.also {
            withTimeout(timeout) { it.await() }
        }
    }
}