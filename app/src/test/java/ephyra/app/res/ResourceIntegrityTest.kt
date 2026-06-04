package ephyra.app.res

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Systemic and comprehensive resource validation test suite.
 *
 * This test statically audits the entire project's resource tree (across all module layers)
 * to catch resource corruptions, invalid SVG vector imports, malformed XML layouts, duplicate
 * declarations, and translation key mismatches before they can trigger runtime crashes on startup.
 */
class ResourceIntegrityTest {

    @Test
    fun auditResourceIntegrity() {
        var rootDir: File? = File(".").absoluteFile
        while (rootDir != null && !File(rootDir, "settings.gradle.kts").exists()) {
            rootDir = rootDir.parentFile
        }
        assertNotNull(rootDir, "Could not find project root directory")
        val finalRootDir = rootDir!!

        // Find all res directories in the workspace, excluding build outputs and IDE files
        val resDirs = mutableListOf<File>()
        finalRootDir.walkTopDown().forEach { file ->
            if (file.isDirectory && file.name == "res" && !file.absolutePath.contains("build") &&
                !file.absolutePath.contains(".gradle") && !file.absolutePath.contains(".idea")
            ) {
                resDirs.add(file)
            }
        }

        assertTrue(resDirs.isNotEmpty(), "No resource directories found in the project")

        val dbFactory = DocumentBuilderFactory.newInstance()
        dbFactory.isNamespaceAware = true
        val dBuilder = dbFactory.newDocumentBuilder()
        val androidNs = "http://schemas.android.com/apk/res/android"

        var xmlFilesChecked = 0
        var vectorElementsChecked = 0
        var stringsFilesChecked = 0
        var translationKeysChecked = 0

        val defaultStringKeys = mutableMapOf<String, Set<String>>() // Path to strings.xml -> Set of keys
        val translationStringKeys = mutableMapOf<String, Set<String>>() // Path to localized strings.xml -> Set of keys

        for (resDir in resDirs) {
            resDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".xml")) {
                    xmlFilesChecked++

                    // --- AUDIT 1: XML Syntax and Parsing ---
                    val doc = try {
                        val parsed = dBuilder.parse(file)
                        parsed.documentElement.normalize()
                        parsed
                    } catch (e: Exception) {
                        // Fail the test if crucial drawables or layouts are malformed
                        val parentDirName = file.parentFile?.name ?: ""
                        if (parentDirName.startsWith("drawable") || parentDirName.startsWith("layout") ||
                            parentDirName.startsWith("values")
                        ) {
                            throw AssertionError("Corrupt or malformed XML resource file: ${file.absolutePath}", e)
                        }
                        return@forEach
                    }

                    val rootTagName = doc.documentElement.localName ?: doc.documentElement.tagName
                    val parentDirName = file.parentFile?.name ?: ""

                    // --- AUDIT 2: Vector Graphic Bounds & Constraints ---
                    if (parentDirName.startsWith("drawable")) {
                        val vectors = mutableListOf<Element>()
                        if (rootTagName == "vector") {
                            vectors.add(doc.documentElement)
                        }

                        val nestedVectors = doc.getElementsByTagNameNS("*", "vector")
                        for (i in 0 until nestedVectors.length) {
                            val node = nestedVectors.item(i)
                            if (node is Element) {
                                vectors.add(node)
                            }
                        }

                        for (vector in vectors) {
                            vectorElementsChecked++
                            val viewportWidth = vector.getAttributeNS(androidNs, "viewportWidth")
                            val viewportHeight = vector.getAttributeNS(androidNs, "viewportHeight")
                            val width = vector.getAttributeNS(androidNs, "width")
                            val height = vector.getAttributeNS(androidNs, "height")

                            assertTrue(
                                viewportWidth.isNotEmpty(),
                                "Vector drawable ${file.absolutePath} is missing viewportWidth",
                            )
                            assertTrue(
                                viewportHeight.isNotEmpty(),
                                "Vector drawable ${file.absolutePath} is missing viewportHeight",
                            )
                            assertTrue(
                                width.isNotEmpty(),
                                "Vector drawable ${file.absolutePath} is missing width dimension",
                            )
                            assertTrue(
                                height.isNotEmpty(),
                                "Vector drawable ${file.absolutePath} is missing height dimension",
                            )

                            val vpWidthVal = viewportWidth.toFloatOrNull()
                            val vpHeightVal = viewportHeight.toFloatOrNull()

                            assertNotNull(
                                vpWidthVal,
                                "Vector drawable ${file.absolutePath} has invalid viewportWidth: '$viewportWidth'",
                            )
                            assertNotNull(
                                vpHeightVal,
                                "Vector drawable ${file.absolutePath} has invalid viewportHeight: '$viewportHeight'",
                            )

                            assertTrue(
                                vpWidthVal!! > 0f,
                                "Vector drawable ${file.absolutePath} has viewportWidth <= 0",
                            )
                            assertTrue(
                                vpHeightVal!! > 0f,
                                "Vector drawable ${file.absolutePath} has viewportHeight <= 0",
                            )
                        }
                    }

                    // --- AUDIT 3: Localized Strings Fallback Audit ---
                    if (parentDirName.startsWith("values") && file.name == "strings.xml") {
                        stringsFilesChecked++
                        val keys = mutableSetOf<String>()
                        val stringNodes = doc.getElementsByTagName("string")
                        for (i in 0 until stringNodes.length) {
                            val stringElem = stringNodes.item(i) as Element
                            val name = stringElem.getAttribute("name")
                            if (name.isNotEmpty()) {
                                keys.add(name)
                                translationKeysChecked++
                            }
                        }

                        val pluralsNodes = doc.getElementsByTagName("plurals")
                        for (i in 0 until pluralsNodes.length) {
                            val pluralsElem = pluralsNodes.item(i) as Element
                            val name = pluralsElem.getAttribute("name")
                            if (name.isNotEmpty()) {
                                keys.add(name)
                                translationKeysChecked++
                            }
                        }

                        val arrayNodes = doc.getElementsByTagName("string-array")
                        for (i in 0 until arrayNodes.length) {
                            val arrayElem = arrayNodes.item(i) as Element
                            val name = arrayElem.getAttribute("name")
                            if (name.isNotEmpty()) {
                                keys.add(name)
                                translationKeysChecked++
                            }
                        }

                        if (parentDirName == "values") {
                            defaultStringKeys[file.absolutePath] = keys
                        } else {
                            translationStringKeys[file.absolutePath] = keys
                        }
                    }
                }
            }
        }

        // Verify fallback matching across translations to guarantee zero Resources$NotFoundException at runtime
        for ((transPath, transKeys) in translationStringKeys) {
            val transFile = File(transPath)
            val moduleResDir = transFile.parentFile?.parentFile ?: continue
            val defaultStringsFile = File(File(moduleResDir, "values"), "strings.xml")

            if (defaultStringsFile.exists()) {
                val defaultKeys = defaultStringKeys[defaultStringsFile.absolutePath] ?: emptySet()
                for (key in transKeys) {
                    assertTrue(
                        defaultKeys.contains(key),
                        "Translation key '$key' found in localized translation ${transFile.absolutePath} but is missing from default file ${defaultStringsFile.absolutePath}! This will trigger Resources\$NotFoundException on default locales.",
                    )
                }
            }
        }

        println(
            "ResourceIntegrityTest: Statically audited $xmlFilesChecked XML resource files. Checked $vectorElementsChecked vectors and $translationKeysChecked string/plurals entries. Verified default fallback matches across $stringsFilesChecked strings files.",
        )
    }

    @Test
    fun auditComposeAnimatedVectorUsage() {
        var rootDir: File? = File(".").absoluteFile
        while (rootDir != null && !File(rootDir, "settings.gradle.kts").exists()) {
            rootDir = rootDir.parentFile
        }
        assertNotNull(rootDir, "Could not find project root directory")
        val finalRootDir = rootDir!!

        val kotlinFiles = mutableListOf<File>()
        finalRootDir.walkTopDown().forEach { file ->
            if (file.isFile && file.name.endsWith(".kt") &&
                file.name != "ResourceIntegrityTest.kt" &&
                !file.absolutePath.contains("build") &&
                !file.absolutePath.contains(".gradle") &&
                !file.absolutePath.contains(".idea")
            ) {
                kotlinFiles.add(file)
            }
        }

        val painterResourceRegex = Regex("""painterResource\s*\(\s*([^)]*)\)""")
        val animReferenceRegex = Regex("""anim_[a-zA-Z0-9_]+""")

        for (file in kotlinFiles) {
            val content = file.readText()
            if (!content.contains("painterResource")) continue

            // 1. Direct violation: painterResource(R.drawable.anim_...)
            val matches = painterResourceRegex.findAll(content)
            for (match in matches) {
                val argument = match.groupValues[1]
                if (argument.contains("anim_")) {
                    throw AssertionError(
                        "File ${file.absolutePath} incorrectly passes an AVD " +
                            "resource to painterResource: '${match.value}'. " +
                            "Only VectorDrawables/rasterized asset types " +
                            "are supported. Use AnimatedImageVector" +
                            ".animatedVectorResource and " +
                            "rememberAnimatedVectorPainter instead.",
                    )
                }
            }

            // 2. Indirect/semi-direct violation:
            // If the file references any 'anim_' drawables, and uses 'painterResource(arg)',
            // check if the argument of painterResource matches any variable or field that was assigned to an anim_ resource,
            // or contains common indicators like 'iconres'.
            val animDrawables = animReferenceRegex.findAll(content).map { it.value }.toSet()
            if (animDrawables.isNotEmpty()) {
                val lines = content.lines()
                val suspectIdentifiers = mutableSetOf<String>()
                for (line in lines) {
                    if (line.contains("anim_")) {
                        val assignmentMatch = Regex("""\b([a-zA-Z_][a-zA-Z0-9_]*)\s*=""").find(line)
                        if (assignmentMatch != null) {
                            suspectIdentifiers.add(assignmentMatch.groupValues[1])
                        }
                    }
                }

                for (match in matches) {
                    val argument = match.groupValues[1]
                    val tokens = Regex("""\b[a-zA-Z_][a-zA-Z0-9_]*\b""")
                        .findAll(argument).map { it.value }.toList()
                    for (token in tokens) {
                        val isSuspect = suspectIdentifiers.contains(token) ||
                            token.lowercase().contains("iconres") ||
                            token.lowercase().contains("tab.icon")
                        if (isSuspect) {
                            throw AssertionError(
                                "File ${file.absolutePath} incorrectly " +
                                    "passes a property/variable '$token' " +
                                    "associated with animated vectors to " +
                                    "painterResource: '${match.value}'. " +
                                    "Use AnimatedImageVector." +
                                    "animatedVectorResource and " +
                                    "rememberAnimatedVectorPainter instead.",
                            )
                        }
                    }
                }
            }
        }
    }
}
