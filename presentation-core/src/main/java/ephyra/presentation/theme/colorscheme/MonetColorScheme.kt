package ephyra.presentation.theme.colorscheme

import android.content.Context
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

internal class MonetColorScheme(context: Context) : BaseColorScheme() {

    override val lightScheme = dynamicLightColorScheme(context)
    override val darkScheme = dynamicDarkColorScheme(context)
}
