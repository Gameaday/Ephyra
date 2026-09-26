package ephyra.feature.reader.fixtures

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Materialises the catalog's *device-sized* fixtures as real, committed files.
 *
 * ## Why this exists
 *
 * `B-037` found that `ReaderFixtureCatalog` declares 14 fixtures and not one exists as a file, so
 * `E4-lab` could not be honestly produced: `E4_ACCEPTANCE.md` §4 requires a `fixture.sha256` and
 * there was nothing to hash. The catalog's `testPath` pointed at `ReaderFixtureCatalogTest.kt`, a
 * Kotlin file, not media.
 *
 * The JVM suites were not wrong, only small. `SyntheticMediaFixtures` generates 80x120 and 108x160
 * thumbnails because rendering a 1080x24000 page on every unit test would be ruinous. The two were
 * conflated, and `ReaderFixtureCatalogTest` only checked the catalog against *itself* — so a 100x
 * dimensional mismatch between declaration and generation passed unnoticed.
 *
 * ## Why the output is committed rather than generated at capture time
 *
 * `E4_ACCEPTANCE.md` §7 requires the fixture binary to be immutable and hash-verified rather than
 * regenerated. Bytes regenerated per run cannot be hash-verified by anyone else, which is the
 * entire point. So they are generated once, here, and committed.
 *
 * Generation is deterministic — flat colour blocks and a fixed grid, no randomness and no
 * timestamps — so re-running must produce byte-identical output.
 *
 * Disabled unless asked, so an ordinary suite run never rewrites the tree:
 *
 * ```
 * ./gradlew :feature:reader:testDebugUnitTest --tests '*FixtureMaterialiser*' \
 *   -Dfixture.materialise=true
 * ```
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class FixtureMaterialiser {

    @Test
    fun `materialise every catalog fixture with declared geometry`() {
        // JUnit 4 `assumeTrue` rather than a conditional-disabled annotation, because Robolectric
        // needs the JUnit 4 runner and this module drives those through the vintage engine.
        assumeTrue(
            "set -Dfixture.materialise=true to regenerate fixtures",
            System.getProperty("fixture.materialise") == "true",
        )

        val dir = File("src/test/resources/fixtures/reader").apply { mkdirs() }
        var written = 0

        for (spec in ReaderFixtureCatalog.pages) {
            // CORRUPT is an explicit non-image and MISSING is explicit absence. Fabricating an
            // image for either would defeat the purpose of the fixture.
            if (spec.behavior == FixtureBehavior.CORRUPT) {
                File(dir, "${spec.id}.bin").writeBytes(byteArrayOf(0, 1, 2, 3, 4, 5))
                written++
                continue
            }
            if (spec.behavior == FixtureBehavior.MISSING) continue
            // JXL has no deterministic encoder in this process; it is a device-codec fixture and
            // stays outstanding under B-029 rather than being faked as a JPEG with the wrong name.
            if (spec.format == FixtureMediaFormat.JXL) continue

            // Animated GIF/WebP are *reviewed immutable binaries* under E4_ACCEPTANCE.md §7, and
            // `SyntheticMediaFixturesTest` already asserts their hashes. They are deliberately not
            // regenerated here: a re-encode would change the bytes and invalidate the very
            // identity that test protects. Left untouched.
            if (spec.format == FixtureMediaFormat.GIF) continue

            val bytes = renderFor(spec)
            File(dir, "${spec.id}.${spec.format.extension()}").writeBytes(bytes)
            assertEquals(
                "${spec.id} must decode at the geometry the catalog declares",
                "${spec.width}x${spec.height}",
                decodeSize(bytes),
            )
            written++
        }
        assertTrue("nothing was materialised", written > 0)
        println("materialised $written fixtures into ${dir.absolutePath}")
    }

    private fun renderFor(spec: ReaderFixtureSpec): ByteArray {
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val w = spec.width.toFloat()
        val h = spec.height.toFloat()

        when (spec.behavior) {
            FixtureBehavior.BORDERED -> {
                // A uniform border of exactly `borderPx` on every edge, with structured artwork
                // inside, so a successful crop and a failed one are both measurable.
                canvas.drawColor(Color.rgb(250, 250, 248))
                val b = spec.borderPx
                paint.color = Color.rgb(24, 48, 96)
                canvas.drawRect(b.toFloat(), b.toFloat(), w - b, h - b, paint)
                paint.color = Color.rgb(200, 48, 48)
                var y = b
                var light = true
                val stripe = 64
                while (y < spec.height - b) {
                    if (light) {
                        canvas.drawRect(
                            b.toFloat(),
                            y.toFloat(),
                            w - b,
                            (y + stripe / 2).toFloat(),
                            paint,
                        )
                    }
                    y += stripe
                    light = !light
                }
            }

            FixtureBehavior.NOISY_BORDER -> {
                // Full-bleed, no uniform margin: a cropper must decline this one.
                canvas.drawColor(Color.rgb(24, 40, 80))
                paint.color = Color.rgb(220, 40, 40)
                canvas.drawRect(0f, 0f, w / 2f, h, paint)
                paint.color = Color.rgb(40, 220, 80)
                canvas.drawRect(w / 2f, 0f, w, h, paint)
            }

            else -> {
                // A quiet, non-uniform page: a regular panel grid, which gives the raster real
                // structure without resembling a uniform border a cropper would trim.
                canvas.drawColor(Color.rgb(246, 244, 240))
                paint.color = Color.rgb(52, 78, 134)
                val cell = 128
                var y = 0
                var row = 0
                while (y < spec.height) {
                    var x = 0
                    while (x < spec.width) {
                        if ((row + x / cell) % 2 == 0) {
                            canvas.drawRect(
                                x + 8f,
                                y + 8f,
                                (x + cell - 8).toFloat(),
                                (y + cell - 8).toFloat(),
                                paint,
                            )
                        }
                        x += cell
                    }
                    y += cell
                    row++
                }
            }
        }
        return encode(spec.format, bitmap)
    }

    private fun encode(format: FixtureMediaFormat, bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        when (format) {
            FixtureMediaFormat.JPEG -> bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            FixtureMediaFormat.PNG -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            FixtureMediaFormat.WEBP -> bitmap.compress(Bitmap.CompressFormat.WEBP, 100, stream)
            else -> error("format $format has no deterministic encoder here")
        }
        return stream.toByteArray()
    }

    private fun decodeSize(bytes: ByteArray): String {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return "${opts.outWidth}x${opts.outHeight}"
    }

    private fun FixtureMediaFormat.extension(): String = when (this) {
        FixtureMediaFormat.JPEG -> "jpg"
        FixtureMediaFormat.PNG -> "png"
        FixtureMediaFormat.WEBP -> "webp"
        FixtureMediaFormat.GIF -> "gif"
        FixtureMediaFormat.JXL -> "jxl"
        FixtureMediaFormat.UNKNOWN -> "bin"
    }
}

/** Shared digest helper, so any evidence record re-derives hashes the same way. */
object FixtureDigest {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
