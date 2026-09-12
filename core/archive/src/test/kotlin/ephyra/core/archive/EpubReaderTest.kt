package ephyra.core.archive

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream

class EpubReaderTest {

    private val archiveReader: ArchiveReader = mockk(relaxed = true)

    @Test
    fun `getChapters parses titles and text content from spine entries`() {
        val containerXml = """
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                </rootfiles>
            </container>
        """.trimIndent()

        val contentOpf = """
            <package version="3.0" xmlns="http://www.idpf.org/2007/opf">
                <manifest>
                    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="chapter2.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine>
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                </spine>
            </package>
        """.trimIndent()

        val chapter1Xhtml = """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 1: The Beginning</title></head>
                <body>
                    <h1>The Beginning</h1>
                    <p>It was a dark and stormy night.</p>
                </body>
            </html>
        """.trimIndent()

        val chapter2Xhtml = """
            <html xmlns="http://www.w3.org/1999/xhtml">
                <head><title>Chapter 2: The Journey</title></head>
                <body>
                    <h2>The Journey</h2>
                    <p>They traveled across the plains.</p>
                </body>
            </html>
        """.trimIndent()

        every { archiveReader.getInputStream("META-INF/container.xml") } answers {
            ByteArrayInputStream(containerXml.toByteArray())
        }
        every { archiveReader.getInputStream("META-INF\\container.xml") } returns null
        every { archiveReader.getInputStream("OEBPS/content.opf") } answers {
            ByteArrayInputStream(contentOpf.toByteArray())
        }
        every { archiveReader.getInputStream("OEBPS/chapter1.xhtml") } answers {
            ByteArrayInputStream(chapter1Xhtml.toByteArray())
        }
        every { archiveReader.getInputStream("OEBPS/chapter2.xhtml") } answers {
            ByteArrayInputStream(chapter2Xhtml.toByteArray())
        }

        val epubReader = EpubReader(archiveReader)
        val chapters = epubReader.getChapters()

        assertEquals(2, chapters.size)
        assertEquals("Chapter 1: The Beginning", chapters[0].title)
        assertEquals("The Beginning It was a dark and stormy night.", chapters[0].bodyText)
        assertEquals("Chapter 2: The Journey", chapters[1].title)
        assertEquals("The Journey They traveled across the plains.", chapters[1].bodyText)
    }
}
