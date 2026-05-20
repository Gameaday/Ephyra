package ephyra.core.common.util.system

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

fun Context.toast(@StringRes resource: Int, duration: Int = Toast.LENGTH_SHORT): Toast {
    return Toast.makeText(this, resource, duration).apply {
        show()
    }
}

fun Context.toast(text: String?, duration: Int = Toast.LENGTH_SHORT): Toast {
    return Toast.makeText(this, text.orEmpty(), duration).apply {
        show()
    }
}
