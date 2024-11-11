package com.greenart7c3.nostrsigner.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.toLowerCase
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.gson.GsonBuilder
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.NostrSigner
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.database.ApplicationEntity
import com.greenart7c3.nostrsigner.database.ApplicationWithPermissions
import com.greenart7c3.nostrsigner.database.HistoryEntity
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.models.BunkerResponse
import com.greenart7c3.nostrsigner.models.IntentData
import com.greenart7c3.nostrsigner.models.Permission
import com.greenart7c3.nostrsigner.models.SignerType
import com.greenart7c3.nostrsigner.models.TimeUtils
import com.greenart7c3.nostrsigner.service.AmberUtils
import com.greenart7c3.nostrsigner.service.ApplicationNameCache
import com.greenart7c3.nostrsigner.service.EventNotificationConsumer
import com.greenart7c3.nostrsigner.service.IntentUtils
import com.greenart7c3.nostrsigner.service.getAppCompatActivity
import com.greenart7c3.nostrsigner.service.model.AmberEvent
import com.greenart7c3.nostrsigner.service.toShortenHex
import com.greenart7c3.nostrsigner.ui.Result
import com.greenart7c3.nostrsigner.ui.navigation.Route
import com.greenart7c3.nostrsigner.ui.theme.orange
import com.vitorpamplona.quartz.crypto.CryptoUtils
import com.vitorpamplona.quartz.encoders.toHexKey
import com.vitorpamplona.quartz.encoders.toNpub
import com.vitorpamplona.quartz.events.LnZapRequestEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiEventHomeScreen(
    intents: List<IntentData>,
    packageName: String?,
    accountParam: Account,
    onLoading: (Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val grouped = intents.groupBy { it.type }.filter { it.key != SignerType.SIGN_EVENT }
    val grouped2 = intents.filter { it.type == SignerType.SIGN_EVENT }.groupBy { it.event?.kind }
    var intentsDialog by remember { mutableStateOf<List<IntentData>?>(null) }
    val acceptEventsGroup1 = grouped.map {
        remember {
            mutableStateOf(true)
        }
    }
    val acceptEventsGroup2 = grouped2.map {
        remember {
            mutableStateOf(true)
        }
    }
    var localAccount by remember { mutableStateOf("") }
    val key = intents.firstOrNull()?.bunkerRequest?.localKey ?: "$packageName"

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intents.firstOrNull()?.currentAccount ?: "",
            )?.signer?.keyPair?.pubKey?.toNpub()?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    if (intentsDialog != null) {
        Dialog(
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
            onDismissRequest = {
                intentsDialog = null
            },
        ) {
            Scaffold(
                bottomBar = {
                    BottomAppBar {
                        IconRow(
                            center = true,
                            title = stringResource(R.string.back_to, Route.IncomingRequest.title),
                            icon = ImageVector.vectorResource(R.drawable.back),
                            onClick = {
                                intentsDialog = null
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(Route.IncomingRequest.title)
                        },
                    )
                },
            ) {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .padding(40.dp),
                ) {
                    var rememberMyChoice by remember { mutableStateOf(intentsDialog!!.first().rememberMyChoice.value) }
                    val first = intentsDialog!!.first()
                    val permission = if (first.type == SignerType.SIGN_EVENT) {
                        Permission("sign_event", first.event!!.kind)
                    } else {
                        Permission(first.type.toString().toLowerCase(Locale.current), null)
                    }

                    val message = if (first.type == SignerType.CONNECT) {
                        stringResource(R.string.connect)
                    } else {
                        permission.toLocalizedString(context)
                    }
                    Text(
                        "$appName is requiring to sign these events related to $message permission.",
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                    )

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .clickable {
                                rememberMyChoice = !rememberMyChoice
                            },
                    ) {
                        Switch(
                            modifier = Modifier.scale(0.85f),
                            checked = rememberMyChoice,
                            onCheckedChange = {
                                rememberMyChoice = !rememberMyChoice
                            },
                        )
                        Text(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                            text = "Always approve this permission",
                        )
                    }

                    intentsDialog?.forEach { intent ->
                        Card(
                            Modifier
                                .padding(4.dp),
                            colors = CardDefaults.cardColors().copy(
                                containerColor = MaterialTheme.colorScheme.background,
                            ),
                            border = BorderStroke(1.dp, Color.Gray),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        intent.checked.value = !intent.checked.value
                                    },
                            ) {
                                Checkbox(
                                    checked = intent.checked.value,
                                    onCheckedChange = { _ ->
                                        intent.checked.value = !intent.checked.value
                                    },
                                    colors = CheckboxDefaults.colors().copy(
                                        uncheckedBorderColor = Color.Gray,
                                    ),
                                )

                                val data = if (intent.type == SignerType.SIGN_EVENT) {
                                    val event = intent.event!!
                                    if (event.kind == 22242) AmberEvent.relay(event) else event.content
                                } else {
                                    intent.encryptedData ?: intent.data
                                }

                                Text(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(vertical = 8.dp),
                                    text = data,
                                    color = if (intent.checked.value) Color.Unspecified else Color.Gray,
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "$appName is requiring some permissions, please review them.",
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
            )

            grouped.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptEventsGroup1,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        intentsDialog = it
                    },
                )
            }
            grouped2.toList().forEachIndexed { index, it ->
                PermissionCard(
                    context = context,
                    acceptEventsGroup = acceptEventsGroup2,
                    index = index,
                    item = it,
                    onDetailsClick = {
                        intentsDialog = it
                    },
                )
            }

            AmberButton(
                Modifier.padding(vertical = 20.dp),
                content = {
                    Text(stringResource(R.string.approve_selected))
                },
                onClick = {
                    onLoading(true)
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val activity = context.getAppCompatActivity()
                            val results = mutableListOf<Result>()
                            reconnectToRelays(intents)

                            for (intentData in intents) {
                                val localAccount =
                                    if (intentData.currentAccount.isNotBlank()) {
                                        LocalPreferences.loadFromEncryptedStorage(
                                            context,
                                            intentData.currentAccount,
                                        )
                                    } else {
                                        accountParam
                                    } ?: continue

                                val key = intentData.bunkerRequest?.localKey ?: packageName ?: continue

                                val database = NostrSigner.getInstance().getDatabase(localAccount.signer.keyPair.pubKey.toNpub())

                                val application =
                                    database
                                        .applicationDao()
                                        .getByKey(key) ?: ApplicationWithPermissions(
                                        application = ApplicationEntity(
                                            key,
                                            "",
                                            listOf(),
                                            "",
                                            "",
                                            "",
                                            localAccount.signer.keyPair.pubKey.toHexKey(),
                                            true,
                                            intentData.bunkerRequest?.secret ?: "",
                                            intentData.bunkerRequest?.secret != null,
                                            localAccount.signPolicy,
                                        ),
                                        permissions = mutableListOf(),
                                    )

                                if (intentData.type == SignerType.SIGN_EVENT) {
                                    val localEvent = intentData.event!!

                                    if (intentData.rememberMyChoice.value) {
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            key,
                                            intentData,
                                            localEvent.kind,
                                            intentData.rememberMyChoice.value,
                                            database,
                                        )
                                    }

                                    database.applicationDao().insertApplicationWithPermissions(application)

                                    database.applicationDao().addHistory(
                                        HistoryEntity(
                                            0,
                                            key,
                                            intentData.type.toString(),
                                            localEvent.kind,
                                            TimeUtils.now(),
                                            intentData.checked.value,
                                        ),
                                    )

                                    if (intentData.bunkerRequest != null) {
                                        if (intentData.checked.value) {
                                            IntentUtils.sendBunkerResponse(
                                                context,
                                                localAccount,
                                                intentData.bunkerRequest,
                                                BunkerResponse(intentData.bunkerRequest.id, localEvent.toJson(), null),
                                                application.application.relays,
                                                onLoading = {},
                                                onDone = {},
                                            )
                                        } else {
                                            AmberUtils.sendBunkerError(
                                                localAccount,
                                                intentData.bunkerRequest,
                                                relays = application.application.relays,
                                                context = context,
                                                onLoading = {},
                                            )
                                        }
                                    } else {
                                        if (intentData.checked.value) {
                                            results.add(
                                                Result(
                                                    null,
                                                    signature = if (localEvent is LnZapRequestEvent &&
                                                        localEvent.tags.any { tag ->
                                                            tag.any { t -> t == "anon" }
                                                        }
                                                    ) {
                                                        localEvent.toJson()
                                                    } else {
                                                        localEvent.sig
                                                    },
                                                    result = if (localEvent is LnZapRequestEvent &&
                                                        localEvent.tags.any { tag ->
                                                            tag.any { t -> t == "anon" }
                                                        }
                                                    ) {
                                                        localEvent.toJson()
                                                    } else {
                                                        localEvent.sig
                                                    },
                                                    id = intentData.id,
                                                ),
                                            )
                                        }
                                    }
                                } else if (intentData.type == SignerType.SIGN_MESSAGE) {
                                    if (intentData.rememberMyChoice.value) {
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            key,
                                            intentData,
                                            null,
                                            intentData.rememberMyChoice.value,
                                            database,
                                        )
                                    }

                                    database.applicationDao().insertApplicationWithPermissions(application)
                                    database.applicationDao().addHistory(
                                        HistoryEntity(
                                            0,
                                            key,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            intentData.checked.value,
                                        ),
                                    )

                                    val signedMessage = CryptoUtils.signString(intentData.data, localAccount.signer.keyPair.privKey!!).toHexKey()

                                    if (intentData.bunkerRequest != null) {
                                        if (intentData.checked.value) {
                                            IntentUtils.sendBunkerResponse(
                                                context,
                                                localAccount,
                                                intentData.bunkerRequest,
                                                BunkerResponse(intentData.bunkerRequest.id, signedMessage, null),
                                                application.application.relays,
                                                onLoading = {},
                                                onDone = {},
                                            )
                                        } else {
                                            AmberUtils.sendBunkerError(
                                                localAccount,
                                                intentData.bunkerRequest,
                                                relays = application.application.relays,
                                                context = context,
                                                onLoading = {},
                                            )
                                        }
                                    } else {
                                        if (intentData.checked.value) {
                                            results.add(
                                                Result(
                                                    null,
                                                    signature = signedMessage,
                                                    result = signedMessage,
                                                    id = intentData.id,
                                                ),
                                            )
                                        }
                                    }
                                } else {
                                    if (intentData.rememberMyChoice.value) {
                                        AmberUtils.acceptOrRejectPermission(
                                            application,
                                            key,
                                            intentData,
                                            null,
                                            intentData.rememberMyChoice.value,
                                            database,
                                        )
                                    }

                                    database.applicationDao().insertApplicationWithPermissions(application)

                                    database.applicationDao().addHistory(
                                        HistoryEntity(
                                            0,
                                            key,
                                            intentData.type.toString(),
                                            null,
                                            TimeUtils.now(),
                                            intentData.checked.value,
                                        ),
                                    )

                                    val signature = intentData.encryptedData ?: continue

                                    if (intentData.bunkerRequest != null) {
                                        if (intentData.checked.value) {
                                            IntentUtils.sendBunkerResponse(
                                                context,
                                                localAccount,
                                                intentData.bunkerRequest,
                                                BunkerResponse(intentData.bunkerRequest.id, signature, null),
                                                application.application.relays,
                                                onLoading = {},
                                                onDone = {},
                                            )
                                        } else {
                                            AmberUtils.sendBunkerError(
                                                localAccount,
                                                intentData.bunkerRequest,
                                                relays = application.application.relays,
                                                context = context,
                                                onLoading = {},
                                            )
                                        }
                                    } else {
                                        if (intentData.checked.value) {
                                            results.add(
                                                Result(
                                                    null,
                                                    signature = signature,
                                                    result = signature,
                                                    id = intentData.id,
                                                ),
                                            )
                                        }
                                    }
                                }
                            }

                            if (results.isNotEmpty()) {
                                sendResultIntent(results, activity)
                            }
                            if (intents.any { it.bunkerRequest != null }) {
                                EventNotificationConsumer(context).notificationManager().cancelAll()
                                finishActivity(activity)
                            } else {
                                finishActivity(activity)
                            }
                        } finally {
                            onLoading(false)
                        }
                    }
                },
            )

            AmberButton(
                Modifier.padding(vertical = 20.dp),
                colors = ButtonDefaults.buttonColors().copy(
                    containerColor = orange,
                ),
                onClick = {
                    val activity = context.getAppCompatActivity()
                    if (intents.any { it.bunkerRequest != null }) {
                        EventNotificationConsumer(context).notificationManager().cancelAll()
                        finishActivity(activity)
                    } else {
                        finishActivity(activity)
                    }
                },
                content = {
                    Text(stringResource(R.string.discard_all_requests))
                },
            )
        }
    }
}

private fun finishActivity(activity: AppCompatActivity?) {
    activity?.intent = null
    activity?.finish()
}

private fun sendResultIntent(
    results: MutableList<Result>,
    activity: AppCompatActivity?,
) {
    val gson = GsonBuilder().serializeNulls().create()
    val json = gson.toJson(results)
    val intent = Intent()
    intent.putExtra("results", json)
    activity?.setResult(Activity.RESULT_OK, intent)
}

private suspend fun reconnectToRelays(intents: List<IntentData>) {
    if (!intents.any { it.bunkerRequest != null }) return

    NostrSigner.getInstance().checkForNewRelays()
}

@Composable
fun ListItem(
    intentData: IntentData,
    packageName: String?,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val key = if (intentData.bunkerRequest != null) {
        intentData.bunkerRequest.localKey
    } else {
        "$packageName"
    }

    var localAccount by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            localAccount = LocalPreferences.loadFromEncryptedStorage(
                context,
                intentData.currentAccount,
            )?.signer?.keyPair?.pubKey?.toNpub()?.toShortenHex() ?: ""
        }
    }

    val appName = ApplicationNameCache.names["$localAccount-$key"] ?: key.toShortenHex()

    Card(
        Modifier
            .padding(4.dp)
            .clickable {
                isExpanded = !isExpanded
            },
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        val name = LocalPreferences.getAccountName(context, intentData.currentAccount)
        Row(
            Modifier
                .fillMaxWidth(),
            Arrangement.Center,
            Alignment.CenterVertically,
        ) {
            Text(
                name.ifBlank { intentData.currentAccount.toShortenHex() },
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                Icons.Default.run {
                    if (isExpanded) {
                        KeyboardArrowDown
                    } else {
                        KeyboardArrowUp
                    }
                },
                contentDescription = "",
                tint = Color.LightGray,
            )
            val text =
                if (intentData.type == SignerType.SIGN_EVENT) {
                    val event = intentData.event!!
                    val permission = Permission("sign_event", event.kind)
                    stringResource(R.string.wants_you_to_sign_a, permission.toLocalizedString(context))
                } else {
                    val permission = Permission(intentData.type.toString().toLowerCase(Locale.current), null)
                    stringResource(R.string.wants_you_to, permission.toLocalizedString(context))
                }
            Text(
                modifier = Modifier.weight(1f),
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(appName)
                    }
                    append(" $text")
                },
                fontSize = 18.sp,
            )

            Switch(
                checked = intentData.checked.value,
                onCheckedChange = { _ ->
                    intentData.checked.value = !intentData.checked.value
                },
            )
        }

        if (isExpanded) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
            ) {
                Text(
                    "Event content",
                    fontWeight = FontWeight.Bold,
                )
                val content =
                    if (intentData.type == SignerType.SIGN_EVENT) {
                        val event = intentData.event!!
                        if (event.kind == 22242) AmberEvent.relay(event) else event.content
                    } else {
                        intentData.data
                    }

                Text(
                    content.take(100),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun PermissionCard(
    context: Context,
    acceptEventsGroup: List<MutableState<Boolean>>,
    index: Int,
    item: Pair<Any?, List<IntentData>>,
    onDetailsClick: (List<IntentData>) -> Unit,
) {
    Card(
        Modifier
            .padding(4.dp),
        colors = CardDefaults.cardColors().copy(
            containerColor = MaterialTheme.colorScheme.background,
        ),
        border = BorderStroke(1.dp, Color.Gray),
    ) {
        Column(
            Modifier
                .padding(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                    },
            ) {
                Checkbox(
                    checked = acceptEventsGroup[index].value,
                    onCheckedChange = { _ ->
                        acceptEventsGroup[index].value = !acceptEventsGroup[index].value
                    },
                    colors = CheckboxDefaults.colors().copy(
                        uncheckedBorderColor = Color.Gray,
                    ),
                )
                val first = item.first
                val permission = if (first is Int) {
                    Permission("sign_event", first)
                } else {
                    Permission(first.toString().toLowerCase(Locale.current), null)
                }

                val message = if (first == SignerType.CONNECT) {
                    stringResource(R.string.connect)
                } else {
                    permission.toLocalizedString(context)
                }

                Text(
                    modifier = Modifier.weight(1f),
                    text = message,
                    color = if (acceptEventsGroup[index].value) Color.Unspecified else Color.Gray,
                )
            }
            if (acceptEventsGroup[index].value) {
                val selected = item.second.filter { it.checked.value }.size
                val total = item.second.size
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "$selected of $total events",
                        modifier = Modifier.padding(start = 48.dp, bottom = 4.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )

                    Text(
                        buildAnnotatedString {
                            withLink(
                                LinkAnnotation.Clickable(
                                    tag = "See_details",
                                    styles = TextLinkStyles(
                                        style = SpanStyle(
                                            textDecoration = TextDecoration.Underline,
                                        ),
                                    ),
                                    linkInteractionListener = {
                                        onDetailsClick(item.second)
                                    },
                                ),
                            ) {
                                append("See details")
                            }
                        },
                        modifier = Modifier.padding(start = 46.dp, bottom = 8.dp),
                        color = Color.Gray,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
