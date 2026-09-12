package ephyra.feature.reader.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.isVisible
import com.google.android.material.R as MaterialR

/**
 * Programmatic error layout shown when a page image fails to load.
 * Replaces the legacy XML layout `reader_error.xml` and ViewBinding.
 */
class ReaderErrorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    val root: ReaderErrorLayout get() = this

    val errorMessage: AppCompatTextView
    val actionRetry: ReaderButton
    val actionOpenInWebView: ReaderButton

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            WRAP_CONTENT,
            Gravity.CENTER,
        )

        val margin8dp = (8 * resources.displayMetrics.density).toInt()

        errorMessage = AppCompatTextView(context).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margin8dp, margin8dp, margin8dp, margin8dp)
            }
            setTextAppearance(context, MaterialR.style.TextAppearance_Material3_BodyMedium)
            setText(ephyra.app.core.common.R.string.decode_image_error)
            gravity = Gravity.CENTER
        }
        addView(errorMessage)

        actionRetry = ReaderButton(context).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margin8dp, margin8dp, margin8dp, margin8dp)
            }
            setText(ephyra.app.core.common.R.string.action_retry)
        }
        addView(actionRetry)

        actionOpenInWebView = ReaderButton(context).apply {
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                setMargins(margin8dp, margin8dp, margin8dp, margin8dp)
            }
            setText(ephyra.app.core.common.R.string.action_open_in_web_view)
            isVisible = false
        }
        addView(actionOpenInWebView)
    }
}
