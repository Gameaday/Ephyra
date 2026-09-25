package ephyra.feature.reader.fixtures

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** A deterministic encoded media artifact created in the test process. */
data class EncodedMediaFixture(
    val id: String,
    val format: FixtureMediaFormat,
    val width: Int,
    val height: Int,
    val bytes: ByteArray,
)

/** Missing media is represented explicitly; it is never fabricated as an empty image. */
data object MissingMediaFixture {
    const val ID = "missing-image-v1"
    val bytes: ByteArray? = null
}

object SyntheticMediaFixtures {
    fun staticJpeg(): EncodedMediaFixture = encoded(
        id = "static-short-v1",
        format = FixtureMediaFormat.JPEG,
        width = 80,
        height = 120,
        fill = Color.rgb(48, 72, 128),
    )

    fun staticPng(): EncodedMediaFixture = encoded(
        id = "static-png-v1",
        format = FixtureMediaFormat.PNG,
        width = 80,
        height = 120,
        fill = Color.rgb(48, 128, 96),
    )

    fun staticWebp(): EncodedMediaFixture = encoded(
        id = "static-webp-v1",
        format = FixtureMediaFormat.WEBP,
        width = 80,
        height = 120,
        fill = Color.rgb(128, 64, 160),
    )

    fun animatedGif(): EncodedMediaFixture = resourceEncoded(
        id = "gif-animation-v1",
        format = FixtureMediaFormat.GIF,
        resource = "fixtures/reader/animated-two-frame-v1.gif",
    )

    fun animatedWebp(): EncodedMediaFixture = resourceEncoded(
        id = "animated-static-compatible-v1",
        format = FixtureMediaFormat.WEBP,
        resource = "fixtures/reader/animated-two-frame-v1.webp",
    )

    fun uniformBorderedJpeg(): EncodedMediaFixture = encoded(
        id = "uniform-bordered-page-v1",
        format = FixtureMediaFormat.JPEG,
        width = 108,
        height = 160,
        fill = Color.WHITE,
        artwork = Color.rgb(72, 72, 72),
        border = 12,
    )

    fun noisyArtworkJpeg(): EncodedMediaFixture {
        val bitmap = Bitmap.createBitmap(108, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(24, 40, 80))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(220, 40, 40)
        canvas.drawRect(0f, 0f, 54f, 160f, paint)
        paint.color = Color.rgb(40, 220, 80)
        canvas.drawRect(54f, 0f, 108f, 160f, paint)
        return encode("noisy-artwork-v1", FixtureMediaFormat.JPEG, bitmap)
    }

    fun corrupt(): EncodedMediaFixture = EncodedMediaFixture(
        id = "corrupt-image-v1",
        format = FixtureMediaFormat.UNKNOWN,
        width = 80,
        height = 120,
        bytes = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05),
    )

    private fun encoded(
        id: String,
        format: FixtureMediaFormat,
        width: Int,
        height: Int,
        fill: Int,
        artwork: Int? = null,
        border: Int = 0,
    ): EncodedMediaFixture {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(fill)
        if (artwork != null && border > 0) {
            canvas.drawColor(artwork)
            canvas.drawRect(
                0f,
                0f,
                border.toFloat(),
                height.toFloat(),
                Paint().apply { color = fill },
            )
            canvas.drawRect(
                (width - border).toFloat(),
                0f,
                width.toFloat(),
                height.toFloat(),
                Paint().apply { color = fill },
            )
            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                border.toFloat(),
                Paint().apply { color = fill },
            )
            canvas.drawRect(
                0f,
                (height - border).toFloat(),
                width.toFloat(),
                height.toFloat(),
                Paint().apply { color = fill },
            )
        }
        return encode(id, format, bitmap)
    }

    private fun resourceEncoded(
        id: String,
        format: FixtureMediaFormat,
        resource: String,
    ): EncodedMediaFixture {
        val bytes = requireNotNull(SyntheticMediaFixtures::class.java.classLoader?.getResourceAsStream(resource)) {
            "Missing reader fixture resource: $resource"
        }.use { it.readBytes() }
        return EncodedMediaFixture(id, format, width = 32, height = 32, bytes = bytes)
    }

    @Suppress("DEPRECATION")
    private fun encode(id: String, format: FixtureMediaFormat, bitmap: Bitmap): EncodedMediaFixture {
        val output = ByteArrayOutputStream()
        val compressFormat = when (format) {
            FixtureMediaFormat.JPEG -> Bitmap.CompressFormat.JPEG
            FixtureMediaFormat.PNG -> Bitmap.CompressFormat.PNG
            FixtureMediaFormat.WEBP -> Bitmap.CompressFormat.WEBP
            else -> error("Unsupported generated fixture format: $format")
        }
        check(bitmap.compress(compressFormat, 100, output)) { "Unable to encode fixture $id" }
        return EncodedMediaFixture(id, format, bitmap.width, bitmap.height, output.toByteArray())
    }
}
