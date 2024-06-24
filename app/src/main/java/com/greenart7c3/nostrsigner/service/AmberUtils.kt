package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.util.Log
import com.greenart7c3.nostrsigner.database.AppDatabase
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationPermissionsEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerRequest
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.SignerType
import com.vitorpamplona.ammolite.relays.Relay
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.HexKey
import com.vitorpamplona.quartz.encoders.hexToByteArray
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.events.Event
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import fr.acinq.secp256k1.Hex

object AmberUtils {
    fun encryptOrDecryptData(
        data: String,
        type: SignerType,
        account: Account,
        pubKey: HexKey,
    ): String? {
        return when (type) {
            SignerType.DECRYPT_ZAP_EVENT -> {
                decryptZapEvent(data, account)
            }
            SignerType.NIP04_DECRYPT -> {
                CryptoUtils.decryptNIP04(
                    data,
                    account.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP04_ENCRYPT -> {
                CryptoUtils.encryptNIP04(
                    data,
                    account.keyPair.privKey!!,
                    Hex.decode(pubKey),
                )
            }
            SignerType.NIP44_ENCRYPT -> {
                CryptoUtils.encryptNIP44v2(
                    data,
                    account.keyPair.privKey!!,
                    pubKey.hexToByteArray(),
                ).encodePayload()
            }
            else -> {
                CryptoUtils.decryptNIP44(
                    data,
                    account.keyPair.privKey!!,
                    pubKey.hexToByteArray(),
                )
            }
        }
    }

    private fun decryptZapEvent(
        data: String,
        account: Account,
    ): String? {
        val event = Event.fromJson(data) as LnZapRequestEvent

        val loggedInPrivateKey = account.keyPair.privKey

        return if (event.isPrivateZap()) {
            val recipientPK = event.zappedAuthor().firstOrNull()
            val recipientPost = event.zappedPost().firstOrNull()
            if (recipientPK == account.keyPair.pubKey.toHexKey()) {
                // if the receiver is logged in, these are the params.
                val pubkeyToUse = event.pubKey

                event.getPrivateZapEvent(loggedInPrivateKey!!, pubkeyToUse)?.toJson() ?: ""
            } else {
                // if the sender is logged in, these are the params
                val altPrivateKeyToUse =
                    if (recipientPost != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPost,
                            event.createdAt,
                        )
                    } else if (recipientPK != null) {
                        LnZapRequestEvent.createEncryptionPrivateKey(
                            loggedInPrivateKey!!.toHexKey(),
                            recipientPK,
                            event.createdAt,
                        )
                    } else {
                        null
                    }

                try {
                    if (altPrivateKeyToUse != null && recipientPK != null) {
                        val altPubKeyFromPrivate = CryptoUtils.pubkeyCreate(altPrivateKeyToUse).toHexKey()

                        if (altPubKeyFromPrivate == event.pubKey) {
                            val result = event.getPrivateZapEvent(altPrivateKeyToUse, recipientPK)
                            result?.toJson() ?: ""
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("Account", "Failed to create pubkey for ZapRequest ${event.id}", e)
                    null
                }
            }
        } else {
            null
        }
    }

    fun sendBunkerError(
        account: Account,
        bunkerRequest: BunkerRequest,
        relays: List<Relay>,
        context: Context,
        onLoading: (Boolean) -> Unit,
    ) {
        IntentUtils.sendBunkerResponse(
            account,
            bunkerRequest.localKey,
            BunkerResponse(bunkerRequest.id, "", "user rejected"),
            relays,
            onLoading = onLoading,
        ) {
            context.getAppCompatActivity()?.intent = null
            context.getAppCompatActivity()?.finish()
        }
    }

    fun acceptOrRejectPermission(
        key: String,
        intentData: IntentData,
        kind: Int?,
        value: Boolean,
        appName: String,
        account: Account,
        database: AppDatabase,
    ) {
        val application =
            database
                .applicationDao()
                .getByKey(key) ?: ApplicationWithPermissions(
                application = ApplicationEntity(
                    key,
                    appName,
                    listOf(),
                    "",
                    "",
                    "",
                    account.keyPair.pubKey.toHexKey(),
                    true,
                    intentData.bunkerRequest?.secret ?: "",
                    intentData.bunkerRequest?.secret != null,
                ),
                permissions = mutableListOf(),
            )

        if (application.permissions.none { it.type == intentData.type.toString() && it.kind == kind }) {
            application.permissions.add(
                ApplicationPermissionsEntity(
                    null,
                    key,
                    intentData.type.toString(),
                    kind,
                    value,
                ),
            )

            database
                .applicationDao()
                .insertApplicationWithPermissions(application)
        }
    }
}

fun String.toShortenHex(): String {
    if (length <= 16) return this
    return replaceRange(8, length - 8, ":")
}
