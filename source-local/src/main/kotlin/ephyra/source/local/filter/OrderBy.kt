package ephyra.source.local.filter

import android.content.Context
import ephyra.core.common.i18n.stringResource
import eu.kanade.tachiyomi.source.model.Filter

sealed class OrderBy(context: Context, selection: Selection) : Filter.Sort(
    context.stringResource(ephyra.app.core.common.R.string.local_filter_order_by),
    arrayOf(context.stringResource(ephyra.app.core.common.R.string.title), context.stringResource(ephyra.app.core.common.R.string.date)),
    selection,
) {
    class Popular(context: Context) : OrderBy(context, Selection(0, true))
    class Latest(context: Context) : OrderBy(context, Selection(1, false))
}
