package ephyra.core.common.util.system

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import dev.icerock.moko.resources.StringResource
import ephyra.core.common.i18n.stringResource

fun Context.toast(@StringRes resource: Int, duration: Int = Toast.LENGTH_SHORT): Toast {
    return Toast.makeText(this, resource, duration).apply {
        show()
    }
}

fun Context.toast(resource: StringResource, duration: Int = Toast.LENGTH_SHORT): Toast {
    return Toast.makeText(this, stringResource(resource), duration).apply {
        show()
    }
}

fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT): Toast {
    return Toast.makeText(this, text.orEmpty(), duration).apply {
        show()
    }
}
