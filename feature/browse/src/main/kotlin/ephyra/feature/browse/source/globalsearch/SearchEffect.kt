package ephyra.feature.browse.source.globalsearch

sealed interface SearchEffect {
    data class ShowToast(val message: String) : SearchEffect
}
