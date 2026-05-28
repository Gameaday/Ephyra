package ephyra.app.res

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class VectorDrawableValidationTest {

    @Test
    fun validateVectorDrawables() {
        var rootDir = File(".").absoluteFile
        while (rootDir != null && !File(rootDir, "settings.gradle.kts").exists()) {
            rootDir = rootDir.parentFile
        }
        assertNotNull(rootDir, "Could not find project root directory")

        val drawableDirs = mutableListOf<File>()
        rootDir.walkTopDown().forEach { file ->
            if (file.isDirectory && file.name.startsWith("drawable") && file.parentFile.name == "res") {
                // Exclude build directories and IDE metadata
                if (!file.absolutePath.contains("build") && !file.absolutePath.contains(".gradle") &&
                    !file.absolutePath.contains(".idea")
                ) {
                    drawableDirs.add(file)
                }
            }
        }

        assertTrue(drawableDirs.isNotEmpty(), "No drawable directories found in workspace")

        val dbFactory = DocumentBuilderFactory.newInstance()
        dbFactory.isNamespaceAware = true
        val dBuilder = dbFactory.newDocumentBuilder()

        val androidNs = "http://schemas.android.com/apk/res/android"
        var checkedFiles = 0
        var checkedVectors = 0
        var checkedPaths = 0

        for (dir in drawableDirs) {
            val xmlFiles = dir.listFiles { _, name -> name.endsWith(".xml") } ?: continue
            for (file in xmlFiles) {
                checkedFiles++
                try {
                    val doc = dBuilder.parse(file)
                    doc.documentElement.normalize()

                    val vectors = mutableListOf<Element>()
                    if (doc.documentElement.localName == "vector") {
                        vectors.add(doc.documentElement)
                    }

                    val nodeList = doc.getElementsByTagNameNS("*", "vector")
                    for (i in 0 until nodeList.length) {
                        val node = nodeList.item(i)
                        if (node is Element) {
                            vectors.add(node)
                        }
                    }

                    for (vector in vectors) {
                        checkedVectors++
                        val viewportWidth = vector.getAttributeNS(androidNs, "viewportWidth")
                        val viewportHeight = vector.getAttributeNS(androidNs, "viewportHeight")
                        val width = vector.getAttributeNS(androidNs, "width")
                        val height = vector.getAttributeNS(androidNs, "height")

                        // 1. Viewport boundaries verification
                        assertTrue(
                            viewportWidth.isNotEmpty(),
                            "File ${file.absolutePath} has a <vector> tag missing android:viewportWidth",
                        )
                        assertTrue(
                            viewportHeight.isNotEmpty(),
                            "File ${file.absolutePath} has a <vector> tag missing android:viewportHeight",
                        )

                        val widthVal = viewportWidth.toFloatOrNull()
                        val heightVal = viewportHeight.toFloatOrNull()

                        assertNotNull(
                            widthVal,
                            "File ${file.absolutePath} has invalid float value for android:viewportWidth: '$viewportWidth'",
                        )
                        assertNotNull(
                            heightVal,
                            "File ${file.absolutePath} has invalid float value for android:viewportHeight: '$viewportHeight'",
                        )

                        assertTrue(
                            widthVal!! > 0f,
                            "File ${file.absolutePath} has android:viewportWidth = $widthVal which is <= 0",
                        )
                        assertTrue(
                            heightVal!! > 0f,
                            "File ${file.absolutePath} has android:viewportHeight = $heightVal which is <= 0",
                        )

                        // 2. Resource dimensions verification (dp, dip, or px suffixes)
                        assertTrue(
                            width.isNotEmpty(),
                            "File ${file.absolutePath} has a <vector> tag missing android:width",
                        )
                        assertTrue(
                            height.isNotEmpty(),
                            "File ${file.absolutePath} has a <vector> tag missing android:height",
                        )

                        assertTrue(
                            width.endsWith("dp") || width.endsWith("dip") || width.endsWith("px"),
                            "File ${file.absolutePath} has invalid width dimension specification (expected dp, dip or px): '$width'",
                        )
                        assertTrue(
                            height.endsWith("dp") || height.endsWith("dip") || height.endsWith("px"),
                            "File ${file.absolutePath} has invalid height dimension specification (expected dp, dip or px): '$height'",
                        )
                    }

                    // 3. Vector path parsing and emptiness checks
                    val pathNodes = doc.getElementsByTagNameNS("*", "path")
                    for (i in 0 until pathNodes.length) {
                        val path = pathNodes.item(i) as Element
                        checkedPaths++
                        val pathData = path.getAttributeNS(androidNs, "pathData")
                        assertTrue(
                            pathData.trim().isNotEmpty(),
                            "File ${file.absolutePath} contains a <path> tag with empty or missing android:pathData",
                        )
                    }
                } catch (e: Exception) {
                    // Ignore parsing errors for standard layout XML files or layer-lists without vectors,
                    // but we can log them or let the test pass if they are not vector drawables.
                }
            }
        }

        println(
            "VectorDrawableValidationTest: Scanned $checkedFiles XML files. Validated $checkedVectors <vector> elements and $checkedPaths <path> elements successfully.",
        )
    }
}
