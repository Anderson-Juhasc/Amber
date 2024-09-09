package com.greenart7c3.nostrsigner.ui

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import com.greenart7c3.nostrsigner.LocalPreferences
import com.greenart7c3.nostrsigner.R
import com.greenart7c3.nostrsigner.models.Account
import com.greenart7c3.nostrsigner.ui.components.TitleExplainer
import java.io.IOException
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

@Composable
fun LanguageScreen(
    modifier: Modifier = Modifier,
    account: Account,
) {
    val context = LocalContext.current
    val languageEntries = remember { context.getLangPreferenceDropdownEntries() }
    val languageList = remember { languageEntries.keys.map { TitleExplainer(it) }.toImmutableList() }
    val languageIndex = getLanguageIndex(languageEntries, account.language)

    Surface(
        modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            Column {
                Box(
                    Modifier
                        .padding(8.dp),
                ) {
                    SettingsRow(
                        R.string.language,
                        R.string.language_description,
                        languageList,
                        languageIndex,
                    ) {
                        account.language = languageEntries[languageList[it].title]
                        LocalPreferences.saveToEncryptedStorage(context, account)
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(account.language),
                        )
                    }
                }
            }
        }
    }
}

fun Context.getLocaleListFromXml(): LocaleListCompat {
    val tagsList = mutableListOf<CharSequence>()
    try {
        val xpp: XmlPullParser = resources.getXml(R.xml.locales_config)
        while (xpp.eventType != XmlPullParser.END_DOCUMENT) {
            if (xpp.eventType == XmlPullParser.START_TAG) {
                if (xpp.name == "locale") {
                    tagsList.add(xpp.getAttributeValue(0))
                }
            }
            xpp.next()
        }
    } catch (e: XmlPullParserException) {
        e.printStackTrace()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return LocaleListCompat.forLanguageTags(tagsList.joinToString(","))
}

fun Context.getLangPreferenceDropdownEntries(): ImmutableMap<String, String> {
    val localeList = getLocaleListFromXml()
    val map = mutableMapOf<String, String>()

    for (a in 0 until localeList.size()) {
        localeList[a].let {
            map.put(
                it!!.getDisplayName(it).replaceFirstChar { char -> char.uppercase() },
                it.toLanguageTag(),
            )
        }
    }
    return map.toImmutableMap()
}

fun getLanguageIndex(
    languageEntries: ImmutableMap<String, String>,
    selectedLanguage: String?,
): Int {
    var languageIndex: Int
    languageIndex =
        if (selectedLanguage != null) {
            languageEntries.values.toTypedArray().indexOf(selectedLanguage)
        } else {
            languageEntries.values.toTypedArray().indexOf(Locale.current.toLanguageTag())
        }
    if (languageIndex == -1) {
        languageIndex = languageEntries.values.toTypedArray().indexOf(Locale.current.language)
    }
    if (languageIndex == -1) languageIndex = languageEntries.values.toTypedArray().indexOf("en")
    return languageIndex
}
