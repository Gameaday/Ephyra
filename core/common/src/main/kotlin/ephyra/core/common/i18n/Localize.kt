package ephyra.core.common.i18n

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

fun Context.stringResource(@StringRes resource: Int): String {
    return getString(resource)
}

fun Context.stringResource(@StringRes resource: Int, vararg args: Any): String {
    return getString(resource, *args)
}

fun Context.pluralStringResource(@PluralsRes resource: Int, count: Int): String {
    return resources.getQuantityString(resource, count, count)
}

fun Context.pluralStringResource(@PluralsRes resource: Int, count: Int, vararg args: Any): String {
    return resources.getQuantityString(resource, count, *args)
}
