import jp.co.soramitsu.iroha2.*
import jp.co.soramitsu.iroha2.generated.crypto.PublicKey
import jp.co.soramitsu.iroha2.generated.datamodel.Value
import jp.co.soramitsu.iroha2.generated.datamodel.account.AccountId
import jp.co.soramitsu.iroha2.generated.datamodel.asset.AssetValue
import jp.co.soramitsu.iroha2.generated.datamodel.asset.AssetValueType
import jp.co.soramitsu.iroha2.generated.datamodel.asset.Mintable
import jp.co.soramitsu.iroha2.generated.datamodel.metadata.Metadata
import jp.co.soramitsu.iroha2.generated.datamodel.name.Name
import jp.co.soramitsu.iroha2.generated.datamodel.predicate.GenericValuePredicateBox
import jp.co.soramitsu.iroha2.generated.datamodel.predicate.value.ValuePredicate
import jp.co.soramitsu.iroha2.query.QueryBuilder
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
    val query = Query (peerUrl, telemetryUrl, admin, adminKeyPair)

    val domain = "domain_${System.currentTimeMillis()}"
    sendTransaction.registerDomain(domain).also { println("DOMAIN $domain CREATED") }

    val joe = "joe_${System.currentTimeMillis()}$ACCOUNT_ID_DELIMITER$domain"
    val joeKeyPair = generateKeyPair()
    sendTransaction.registerAccount(joe, listOf(joeKeyPair.public.toIrohaPublicKey()))
        .also { println("ACCOUNT $joe CREATED") }

    val assetDefinition = "asset_${System.currentTimeMillis()}$ASSET_ID_DELIMITER$domain"
    sendTransaction.registerAssetDefinition(assetDefinition, AssetValueType.Quantity())
        .also { println("ASSET DEFINITION $assetDefinition CREATED") }

    val joeAsset = "$assetDefinition$ASSET_ID_DELIMITER$joe"
    sendTransaction.registerAsset(joeAsset, AssetValue.Quantity(100))
        .also { println("ASSET $joeAsset CREATED") }

    val carl = "carl_${System.currentTimeMillis()}$ACCOUNT_ID_DELIMITER$domain"
    sendTransaction.registerAccount(carl, listOf())
        .also { println("ACCOUNT $carl CREATED") }

    val carlAsset = "$assetDefinition$ASSET_ID_DELIMITER$carl"
    sendTransaction.registerAsset(carlAsset, AssetValue.Quantity(0))
        .also { println("ASSET $carlAsset CREATED") }

    sendTransaction.transferAsset(joeAsset, 10, carlAsset, joe.asAccountId(), joeKeyPair)
        .also { println("$joe TRANSFERRED FROM $joeAsset TO $carlAsset: 10") }
    sendTransaction.getAccountAmount(joe, joeAsset).also { println("$joeAsset BALANCE: $it") }
    sendTransaction.getAccountAmount(carl, carlAsset).also { println("$carlAsset BALANCE: $it") }

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

    suspend fun registerAssetDefinition(
        id: String,
        type: AssetValueType = AssetValueType.Store(),
        metadata: Map<Name, Value> = mapOf(),
        mintable: Mintable = Mintable.Infinitely(),
        admin: AccountId = this.admin,
        keyPair: KeyPair = this.keyPair
    ) {
        client.sendTransaction {
            account(admin)
            this.registerAssetDefinition(id.asAssetDefinitionId(), type, Metadata(metadata), mintable)
            buildSigned(keyPair)
        }.also {
            withTimeout(timeout) { it.await() }
        }
    }

    suspend fun registerAsset(
        id: String,
        value: AssetValue,
        admin: AccountId = this.admin,
        keyPair: KeyPair = this.keyPair
    ) {
        client.sendTransaction {
            account(admin)
            this.registerAsset(id.asAssetId(), value)
            buildSigned(keyPair)
        }.also {
            withTimeout(timeout) { it.await() }
        }
    }

    suspend fun transferAsset(
        from: String,
        value: Int,
        to: String,
        admin: AccountId = this.admin,
        keyPair: KeyPair = this.keyPair
    ) {
        client.sendTransaction {
            account(admin)
            this.transferAsset(from.asAssetId(), value, to.asAssetId())
            buildSigned(keyPair)
        }.also {
            withTimeout(timeout) { it.await() }
        }
    }

    suspend fun getAccountAmount(accountId: String, assetId: String): Long {
        return QueryBuilder.findAccountById(accountId.asAccountId())
            .account(admin)
            .buildSigned(keyPair)
            .let { query ->
                client.sendQuery(query).assets[assetId.asAssetId()]?.value
            }.let { value ->
                value?.cast<AssetValue.Quantity>()?.u32
            } ?: throw RuntimeException("NOT FOUND")
    }
}

open class Query (peerUrl: String,
                  telemetryUrl: String,
                  private val admin: AccountId,
                  private val keyPair: KeyPair) {

    private val client = AdminIroha2Client(URL(peerUrl), URL(telemetryUrl), log = true)

    suspend fun findAllAssets(queryFilter: GenericValuePredicateBox<ValuePredicate>? = null) = QueryBuilder
        .findAllAssets(queryFilter)
        .account(admin)
        .buildSigned(keyPair)
        .let { client.sendQuery(it) }
}