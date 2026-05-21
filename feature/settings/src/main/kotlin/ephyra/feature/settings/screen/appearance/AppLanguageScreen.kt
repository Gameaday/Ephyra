package ephyra.feature.settings.screen.appearance

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.LocaleListCompat
import androidx.navigation.NavController
import ephyra.core.common.i18n.stringResource
import ephyra.core.common.util.system.LocaleHelper
import ephyra.presentation.core.components.AppBar
import ephyra.presentation.core.components.material.Scaffold
import ephyra.presentation.core.i18n.stringResource
import ephyra.presentation.core.ui.navigation.LocalNavController
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.xmlpull.v1.XmlPullParser

@Composable
fun AppLanguageScreen(
    navController: NavController = LocalNavController.current,
) {
    val context = LocalContext.current

    val langs = remember { getLangs(context) }
    var currentLanguage by remember {
        mutableStateOf(AppCompatDelegate.getApplicationLocales().get(0)?.toLanguageTag() ?: "")
    }

    LaunchedEffect(currentLanguage) {
        val locale = if (currentLanguage.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(currentLanguage)
        }
        AppCompatDelegate.setApplicationLocales(locale)
    }

    Scaffold(
        topBar = { scrollBehavior ->
            AppBar(
                title = stringResource(ephyra.app.core.common.R.string.pref_app_language),
                navigateUp = { navController.popBackStack() },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.padding(contentPadding),
        ) {
            items(langs, key = { it.langTag }) {
                ListItem(
                    modifier = Modifier.clickable {
                        currentLanguage = it.langTag
                    },
                    headlineContent = { Text(it.displayName) },
                    supportingContent = {
                        it.localizedDisplayName?.let {
                            Text(it)
                        }
                    },
                    trailingContent = {
                        if (currentLanguage == it.langTag) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

private fun getLangs(context: Context): ImmutableList<Language> {
    val langs = mutableListOf<Language>()
    val parser = context.resources.getXml(
        context.resources.getIdentifier("locales_config", "xml", context.packageName),
    )
    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        if (eventType == XmlPullParser.START_TAG && parser.name == "locale") {
            for (i in 0..<parser.attributeCount) {
                if (parser.getAttributeName(i) == "name") {
                    val langTag = parser.getAttributeValue(i)
                    val displayName = LocaleHelper.getLocalizedDisplayName(langTag)
                    if (displayName.isNotEmpty()) {
                        langs.add(Language(langTag, displayName, LocaleHelper.getDisplayName(langTag)))
                    }
                }
            }
        }
        eventType = parser.next()
    }

    langs.sortBy { it.displayName }
    langs.add(0, Language("", context.stringResource(ephyra.app.core.common.R.string.label_default), null))

    return langs.toImmutableList()
}

private data class Language(
    val langTag: String,
    val displayName: String,
    val localizedDisplayName: String?,
)
