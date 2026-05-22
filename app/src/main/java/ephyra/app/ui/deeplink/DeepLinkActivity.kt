package ephyra.app.ui.deeplink

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.content.IntentSanitizer
import ephyra.app.ui.main.MainActivity

class DeepLinkActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sanitizedIntent = IntentSanitizer.Builder()
            .allowAnyComponent()
            .allowAction(Intent.ACTION_SEARCH)
            .allowAction("com.google.android.gms.actions.SEARCH_ACTION")
            .allowAction("ephyra.app.SEARCH")
            .allowAction(Intent.ACTION_SEND)
            .allowType("text/plain")
            .allowExtra("query") { it is String }
            .allowExtra(Intent.EXTRA_TEXT) { it is String }
            .build()
            .sanitizeByThrowing(intent)

        sanitizedIntent.apply {
            flags = flags or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            setClass(applicationContext, MainActivity::class.java)
        }
        startActivity(sanitizedIntent)
        finish()
    }
}
