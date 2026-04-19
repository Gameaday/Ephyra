package ephyra.app.crash

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ephyra.app.ui.main.MainActivity

/**
 * Fallback crash UI shown when the app fails before Koin is initialised.
 *
 * [CrashActivity] extends [ephyra.presentation.core.ui.activity.BaseActivity] which eagerly
 * resolves Koin delegates (`KoinJavaComponent.get(...)`).  Launching it before
 * `startKoin()` completes therefore causes a second crash that silently swallows the
 * original error.
 *
 * This activity has zero Koin dependencies and uses only plain Android Views so it works
 * regardless of DI state.  It is exclusively used by the [App.onCreate] catch-block around
 * `startKoin()`.
 *
 * The activity runs in the default (main-app) process, not `:error_handler`, so it can be
 * shown even when the Koin-dependent process bootstrap is broken.
 */
class StartupFailureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STACK_TRACE = "stack_trace"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "Unknown error"

        val scrollView = ScrollView(this).apply {
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val titleText = TextView(this).apply {
            text = "App failed to start"
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val bottomMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = bottomMargin }
        }

        val subtitleText = TextView(this).apply {
            text = "A critical dependency could not be initialised. " +
                "Please report this error or try reinstalling the app."
            textSize = 14f
            val bottomMargin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = bottomMargin }
        }

        val traceText = TextView(this).apply {
            text = stackTrace
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
            val bottomMargin = (16 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = bottomMargin }
        }

        val restartButton = Button(this).apply {
            text = "Restart App"
            setOnClickListener {
                val restart = Intent(this@StartupFailureActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                finishAffinity()
                startActivity(restart)
            }
        }

        container.addView(titleText)
        container.addView(subtitleText)
        container.addView(traceText)
        container.addView(restartButton)
        scrollView.addView(container)
        setContentView(scrollView)
    }
}
