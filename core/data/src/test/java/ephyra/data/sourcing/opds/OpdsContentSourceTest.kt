package ephyra.data.sourcing.opds

import ephyra.domain.content.model.ContentPage
import ephyra.domain.content.model.ContentType
import ephyra.domain.content.model.FilterSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpdsContentSourceTest {

    private val client = mockk<OkHttpClient>()
    private lateinit var source: OpdsContentSource

    @Before
    fun setUp() {
        source = OpdsContentSource(
            id = "opds-test",
            name = "Test OPDS Catalog",
            baseUrl = "https://kavita.example.com/api/opds",
            client = client,
        )
    }

    @Test
    fun `source metadata conforms to contract`() {
        assertEquals("opds-test", source.id)
        assertEquals("Test OPDS Catalog", source.name)
        assertTrue(source.supportedTypes.contains(ContentType.BOOK))
        assertTrue(source.supportedTypes.contains(ContentType.MANGA))
    }

    @Test
    fun `parseOpds1XmlCatalog parses Atom XML feed accurately`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:opds="http://opds-spec.org/2010/catalog">
                <title>Kavita OPDS Catalog</title>
                <entry>
                    <id>urn:kavita:series:42</id>
                    <title>Dungeon Meshi</title>
                    <author><name>Ryoko Kui</name></author>
                    <summary>Delicious cooking in a fantasy dungeon dungeon adventure.</summary>
                    <link rel="http://opds-spec.org/image" href="/covers/42.jpg" type="image/jpeg"/>
                    <link rel="http://opds-spec.org/acquisition" href="/download/42.cbz" type="application/x-cbz"/>
                </entry>
                <entry>
                    <id>urn:kavita:series:43</id>
                    <title>Witch Hat Atelier</title>
                    <author><name>Kamome Shirahama</name></author>
                    <link rel="http://opds-spec.org/thumbnail" href="/covers/43.jpg" type="image/jpeg"/>
                    <link rel="subsection" href="/series/43" type="application/atom+xml"/>
                </entry>
            </feed>
        """.trimIndent()

        val entries = source.parseOpds1XmlCatalog(xml)
        assertEquals(2, entries.size)

        val first = entries[0]
        assertEquals("Dungeon Meshi", first.title)
        assertEquals("Ryoko Kui", first.author)
        assertEquals("https://kavita.example.com/api/opds/covers/42.jpg", first.coverUrl)
        assertEquals("https://kavita.example.com/api/opds/download/42.cbz", first.url)
        assertEquals("Delicious cooking in a fantasy dungeon dungeon adventure.", first.description)

        val second = entries[1]
        assertEquals("Witch Hat Atelier", second.title)
        assertEquals("https://kavita.example.com/api/opds/covers/43.jpg", second.coverUrl)
        assertEquals("https://kavita.example.com/api/opds/series/43", second.url)
    }

    @Test
    fun `parseOpds2JsonCatalog parses OPDS 2_0 JSON publication feed accurately`() {
        val json = """
            {
              "metadata": {
                "title": "Jellyfin Books"
              },
              "publications": [
                {
                  "metadata": {
                    "title": "Ascendance of a Bookworm",
                    "author": "Miya Kazuki",
                    "description": "A book lover reborn in another world without books."
                  },
                  "images": [
                    { "href": "/Items/101/Images/Primary", "type": "image/jpeg" }
                  ],
                  "links": [
                    { "rel": "http://opds-spec.org/acquisition", "href": "/Items/101/Download", "type": "application/epub+zip" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = source.parseOpds2JsonCatalog(json)
        assertEquals(1, entries.size)

        val pub = entries[0]
        assertEquals("Ascendance of a Bookworm", pub.title)
        assertEquals("Miya Kazuki", pub.author)
        assertEquals("https://kavita.example.com/api/opds/Items/101/Images/Primary", pub.coverUrl)
        assertEquals("https://kavita.example.com/api/opds/Items/101/Download", pub.url)
        assertEquals("A book lover reborn in another world without books.", pub.description)
    }

    @Test
    fun `getCatalog executes network call and decodes feed`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
                <entry>
                    <id>1</id>
                    <title>Calibre Book</title>
                    <link rel="http://opds-spec.org/acquisition" href="/book1.epub" type="application/epub+zip"/>
                </entry>
            </feed>
        """.trimIndent()

        val mockCall = mockk<Call>()
        val response = Response.Builder()
            .request(Request.Builder().url("https://kavita.example.com/api/opds").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(xml.toResponseBody("application/atom+xml".toMediaType()))
            .build()

        every { client.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns response

        val catalog = source.getCatalog(1, FilterSet())
        assertEquals(1, catalog.size)
        assertEquals("Calibre Book", catalog[0].title)
    }

    @Test
    fun `loadPage returns ImagePage with target url`() = runTest {
        val page = source.loadPage("https://kavita.example.com/page1.jpg", 0)
        assertTrue(page is ContentPage.ImagePage)
        assertEquals(0, page.index)
        assertEquals("https://kavita.example.com/page1.jpg", (page as ContentPage.ImagePage).imageUrl)
    }
}
