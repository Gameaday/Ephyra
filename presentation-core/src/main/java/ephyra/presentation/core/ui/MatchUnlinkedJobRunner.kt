package ephyra.presentation.core.ui

import android.content.Context

interface MatchUnlinkedJobRunner {
    fun isRunning(context: Context): Boolean
    fun start(context: Context)
}
