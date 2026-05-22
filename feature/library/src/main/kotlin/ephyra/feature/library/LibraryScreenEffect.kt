package ephyra.feature.library

sealed interface LibraryScreenEffect {
    data class NavigateToManga(val mangaId: Long) : LibraryScreenEffect
    data class ShowSnackbar(val messageRes: Int, val categoryName: String? = null) : LibraryScreenEffect
    data object NavigateToCategorySettings : LibraryScreenEffect
    data class NavigateToGlobalSearch(val query: String?) : LibraryScreenEffect
}
