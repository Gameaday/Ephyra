package ephyra.feature.upcoming

import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapIndexedNotNull
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ephyra.core.common.util.insertSeparatorsReversed
import ephyra.core.common.util.lang.toLocalDate
import ephyra.domain.manga.model.Manga
import ephyra.domain.upcoming.interactor.GetUpcomingManga
import ephyra.presentation.core.udf.BaseUdfViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class UpcomingScreenModel @Inject constructor(
    private val getUpcomingManga: GetUpcomingManga,
) : BaseUdfViewModel<UpcomingScreenModel.State, UpcomingScreenEvent, UpcomingScreenEffect>(State()) {

    init {
        viewModelScope.launch {
            getUpcomingManga.subscribe().collectLatest {
                updateState { state ->
                    val upcomingItems = it.toUpcomingUIModels()
                    state.copy(
                        items = upcomingItems,
                        events = upcomingItems.toEvents(),
                        headerIndexes = upcomingItems.getHeaderIndexes(),
                    )
                }
            }
        }
    }

    private fun List<Manga>.toUpcomingUIModels(): ImmutableList<UpcomingUIModel> {
        var mangaCount = 0
        return fastMap { UpcomingUIModel.Item(it) }
            .insertSeparatorsReversed { before, after ->
                if (after != null) mangaCount++

                val beforeDate = before?.manga?.expectedNextUpdate?.toLocalDate()
                val afterDate = after?.manga?.expectedNextUpdate?.toLocalDate()

                if (beforeDate != afterDate && afterDate != null) {
                    UpcomingUIModel.Header(afterDate, mangaCount).also { mangaCount = 0 }
                } else {
                    null
                }
            }
            .toImmutableList()
    }

    private fun List<UpcomingUIModel>.toEvents(): ImmutableMap<LocalDate, Int> {
        return filterIsInstance<UpcomingUIModel.Header>()
            .associate { it.date to it.mangaCount }
            .toImmutableMap()
    }

    private fun List<UpcomingUIModel>.getHeaderIndexes(): ImmutableMap<LocalDate, Int> {
        return fastMapIndexedNotNull { index, upcomingUIModel ->
            if (upcomingUIModel is UpcomingUIModel.Header) {
                upcomingUIModel.date to index
            } else {
                null
            }
        }
            .toMap()
            .toImmutableMap()
    }

    override fun onEvent(event: UpcomingScreenEvent) {
        when (event) {
            is UpcomingScreenEvent.SetSelectedYearMonth -> setSelectedYearMonth(event.yearMonth)
            is UpcomingScreenEvent.ClickUpcoming -> handleClickUpcoming(event.mangaId)
        }
    }

    private fun setSelectedYearMonth(yearMonth: YearMonth) {
        updateState { it.copy(selectedYearMonth = yearMonth) }
    }

    private fun handleClickUpcoming(mangaId: Long) {
        emitEffect(UpcomingScreenEffect.NavigateToMangaDetails(mangaId))
    }

    data class State(
        val selectedYearMonth: YearMonth = YearMonth.now(),
        val items: ImmutableList<UpcomingUIModel> = persistentListOf(),
        val events: ImmutableMap<LocalDate, Int> = persistentMapOf(),
        val headerIndexes: ImmutableMap<LocalDate, Int> = persistentMapOf(),
    )
}
