package ephyra.app.health

/**
 * Parsed form of `health-baseline.json`.
 *
 * Parsed with a deliberately small regex reader rather than a serialization dependency: this runs
 * on every build and must not add compile time, which is the very thing it is measuring.
 */
data class HealthBaseline(
    val moduleCount: Int,
    val projectDependencyEdges: Int,
    val mainSourceFiles: Int,
    val testSourceFiles: Int,
    val todoFixmeMarkers: Int,
    val deprecatedMarkers: Int,
    val releaseApkMibPerAbi: Double,
) {
    companion object {
        fun parse(json: String): HealthBaseline = HealthBaseline(
            moduleCount = json.int("moduleCount"),
            projectDependencyEdges = json.int("projectDependencyEdges"),
            mainSourceFiles = json.int("mainSourceFiles"),
            testSourceFiles = json.int("testSourceFiles"),
            todoFixmeMarkers = json.int("todoFixmeMarkers"),
            deprecatedMarkers = json.int("deprecatedMarkers"),
            releaseApkMibPerAbi = json.double("releaseApkMibPerAbi"),
        )

        private fun String.int(key: String): Int = double(key).toInt()

        private fun String.double(key: String): Double =
            Regex("\"$key\"\\s*:\\s*([0-9.]+)").find(this)?.groupValues?.get(1)?.toDouble()
                ?: error("health-baseline.json is missing required key: $key")
    }
}
