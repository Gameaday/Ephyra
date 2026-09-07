package ephyra.feature.manga

sealed interface MangaScreenEffect {
    data class ShowToast(val message: String) : MangaScreenEffect
    data class ShowSnackbar(val message: String) : MangaScreenEffect
}
