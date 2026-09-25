package ephyra.feature.reader.fixtures

enum class FixtureMediaFormat { JPEG, PNG, WEBP, GIF, JXL, UNKNOWN }

enum class FixtureBehavior {
    STATIC,
    LONG_STRIP,
    BORDERED,
    NOISY_BORDER,
    TALL_PAGED,
    ANIMATED,
    CORRUPT,
    MISSING,
    REVISION_CHANGE,
}

data class ReaderFixtureSpec(
    val id: String,
    val behavior: FixtureBehavior,
    val format: FixtureMediaFormat,
    val width: Int,
    val height: Int,
    val borderPx: Int = 0,
    val expectedContentRect: IntArray? = null,
    val requiresSlicing: Boolean = false,
    val cropExpected: Boolean = false,
    val supportsPaged: Boolean = true,
    val supportsContinuous: Boolean = true,
    val sourceModes: Set<String> = setOf("online", "downloaded", "local"),
    val testPath: String,
) {
    init {
        require(id.isNotBlank())
        require(width > 0 && height > 0)
        require(borderPx in 0..minOf(width, height))
        require(expectedContentRect == null || expectedContentRect.size == 4)
        require(testPath.isNotBlank())
    }
}

data class ReaderInteractionFixture(val id: String, val description: String, val required: Boolean)

object ReaderFixtureCatalog {
    private const val TEST_PATH =
        "feature/reader/src/test/kotlin/ephyra/feature/reader/fixtures/ReaderFixtureCatalogTest.kt"

    val pages: List<ReaderFixtureSpec> =
        listOf(
            ReaderFixtureSpec(
                "static-short-v1",
                FixtureBehavior.STATIC,
                FixtureMediaFormat.JPEG,
                800,
                1200,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "static-webp-v1",
                FixtureBehavior.STATIC,
                FixtureMediaFormat.WEBP,
                800,
                1200,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "static-long-webtoon-v1",
                FixtureBehavior.LONG_STRIP,
                FixtureMediaFormat.JPEG,
                800,
                12000,
                requiresSlicing = true,
                supportsPaged = false,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "uniform-bordered-page-v1", FixtureBehavior.BORDERED, FixtureMediaFormat.JPEG, 1080, 24000,
                12, intArrayOf(12, 12, 1068, 23988), cropExpected = true, supportsPaged = false, testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "noisy-artwork-v1",
                FixtureBehavior.NOISY_BORDER,
                FixtureMediaFormat.JPEG,
                1080,
                24000,
                cropExpected = false,
                supportsPaged = false,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "tall-paged-image-v1",
                FixtureBehavior.TALL_PAGED,
                FixtureMediaFormat.JPEG,
                1080,
                12000,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "animated-static-compatible-v1",
                FixtureBehavior.ANIMATED,
                FixtureMediaFormat.WEBP,
                800,
                1200,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "gif-animation-v1",
                FixtureBehavior.ANIMATED,
                FixtureMediaFormat.GIF,
                640,
                900,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "jxl-page-v1",
                FixtureBehavior.STATIC,
                FixtureMediaFormat.JXL,
                1080,
                16000,
                supportsContinuous = false,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "corrupt-image-v1",
                FixtureBehavior.CORRUPT,
                FixtureMediaFormat.UNKNOWN,
                800,
                1200,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "missing-image-v1",
                FixtureBehavior.MISSING,
                FixtureMediaFormat.UNKNOWN,
                800,
                1200,
                testPath = TEST_PATH,
            ),
            ReaderFixtureSpec(
                "source-revision-change-v1",
                FixtureBehavior.REVISION_CHANGE,
                FixtureMediaFormat.JPEG,
                800,
                1200,
                testPath = TEST_PATH,
            ),
        )

    val interactions: List<ReaderInteractionFixture> = listOf(
        ReaderInteractionFixture("multi-pointer-pinch-v1", "Two pointers claim and drive one transform.", true),
        ReaderInteractionFixture(
            "pinch-after-parent-scroll-v1",
            "Second pointer survives parent scroll consumption.",
            true,
        ),
        ReaderInteractionFixture("double-tap-versus-single-v1", "Second tap cancels deferred first tap.", true),
        ReaderInteractionFixture("predictive-back-v1", "Back gesture owns route transition and shared element.", true),
        ReaderInteractionFixture(
            "compact-expanded-scenes-v1",
            "Reader and shell geometry cover compact/expanded scenes.",
            true,
        ),
    )
}
