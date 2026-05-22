package ephyra.feature.upcoming

sealed interface UpcomingScreenEffect {
    data class NavigateToMangaDetails(val mangaId: Long) : UpcomingScreenEffect
}
