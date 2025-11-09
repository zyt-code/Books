package com.androidbooks.data.epub

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class EpubParser {

    /**
     * Parse EPUB file and extract metadata, spine, TOC, and cover
     */
    suspend fun parseEpub(epubFile: File, outputDir: File): Result<EpubBook> {
        return try {
            ZipFile(epubFile).use { zipFile ->
                // Step 1: Parse container.xml to find content.opf location
                val contentOpfPath = parseContainer(zipFile)
                    ?: return Result.failure(Exception("container.xml not found or invalid"))

                // Step 2: Parse content.opf
                val opfBasePath = contentOpfPath.substringBeforeLast("/", "")
                val opfEntry = zipFile.getEntry(contentOpfPath)
                    ?: return Result.failure(Exception("content.opf not found at $contentOpfPath"))

                val opfData = parseOpf(zipFile.getInputStream(opfEntry), opfBasePath)

                // Step 3: Extract cover image if available
                val coverImagePath = opfData.coverId?.let { coverId ->
                    extractCoverImage(zipFile, opfData.manifestItems[coverId], outputDir, opfBasePath)
                }

                // Step 4: Extract all spine HTML files to cache
                extractSpineFiles(zipFile, opfData.spineItems, outputDir, opfBasePath)

                // Step 5: Parse TOC (NCX or Nav XHTML)
                val toc = parseToc(zipFile, opfData.tocHref, opfBasePath)

                Result.success(
                    EpubBook(
                        metadata = opfData.metadata,
                        spineItems = opfData.spineItems,
                        toc = toc,
                        coverImagePath = coverImagePath
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseContainer(zipFile: ZipFile): String? {
        val containerEntry = zipFile.getEntry("META-INF/container.xml") ?: return null
        return zipFile.getInputStream(containerEntry).use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                    return@use parser.getAttributeValue(null, "full-path")
                }
                eventType = parser.next()
            }
            null
        }
    }

    private data class OpfData(
        val metadata: EpubMetadata,
        val manifestItems: Map<String, String>, // id -> href
        val spineItems: List<SpineItem>,
        val tocHref: String?,
        val coverId: String?
    )

    private fun parseOpf(input: InputStream, basePath: String): OpfData {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var title = "Unknown"
        var author = "Unknown Author"
        var language: String? = null
        var publisher: String? = null
        var coverId: String? = null
        val manifestItems = mutableMapOf<String, String>()
        val spineItemIds = mutableListOf<String>()
        var tocHref: String? = null

        var eventType = parser.eventType
        var currentTag: String? = null

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (parser.name) {
                        "meta" -> {
                            if (parser.getAttributeValue(null, "name") == "cover") {
                                coverId = parser.getAttributeValue(null, "content")
                            }
                        }
                        "item" -> {
                            val id = parser.getAttributeValue(null, "id")
                            val href = parser.getAttributeValue(null, "href")
                            val properties = parser.getAttributeValue(null, "properties")
                            val mediaType = parser.getAttributeValue(null, "media-type")

                            if (id != null && href != null) {
                                manifestItems[id] = href

                                // Check for cover image
                                if (properties?.contains("cover-image") == true) {
                                    coverId = id
                                }

                                // Check for TOC
                                if (mediaType == "application/x-dtbncx+xml") {
                                    tocHref = href
                                }
                            }
                        }
                        "itemref" -> {
                            val idref = parser.getAttributeValue(null, "idref")
                            if (idref != null) {
                                spineItemIds.add(idref)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim() ?: ""
                    when (currentTag) {
                        "dc:title", "title" -> if (text.isNotEmpty()) title = text
                        "dc:creator", "creator" -> if (text.isNotEmpty()) author = text
                        "dc:language", "language" -> if (text.isNotEmpty()) language = text
                        "dc:publisher", "publisher" -> if (text.isNotEmpty()) publisher = text
                    }
                }
            }
            eventType = parser.next()
        }

        // Build spine items list
        val spineItems = spineItemIds.mapNotNull { id ->
            manifestItems[id]?.let { href ->
                SpineItem(
                    id = id,
                    href = if (basePath.isNotEmpty()) "$basePath/$href" else href,
                    mediaType = "application/xhtml+xml"
                )
            }
        }

        return OpfData(
            metadata = EpubMetadata(title, author, language, publisher, coverId),
            manifestItems = manifestItems,
            spineItems = spineItems,
            tocHref = tocHref?.let { if (basePath.isNotEmpty()) "$basePath/$it" else it },
            coverId = coverId
        )
    }

    private fun extractCoverImage(
        zipFile: ZipFile,
        coverHref: String?,
        outputDir: File,
        basePath: String
    ): String? {
        if (coverHref == null) return null

        val fullPath = if (basePath.isNotEmpty()) "$basePath/$coverHref" else coverHref
        val coverEntry = zipFile.getEntry(fullPath) ?: return null

        val coverFile = File(outputDir, "cover.jpg")
        zipFile.getInputStream(coverEntry).use { input ->
            coverFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return coverFile.absolutePath
    }

    private fun extractSpineFiles(
        zipFile: ZipFile,
        spineItems: List<SpineItem>,
        outputDir: File,
        basePath: String
    ) {
        val contentDir = File(outputDir, "content")
        contentDir.mkdirs()

        spineItems.forEachIndexed { index, item ->
            val entry = zipFile.getEntry(item.href) ?: return@forEachIndexed
            val outputFile = File(contentDir, "spine_$index.xhtml")

            zipFile.getInputStream(entry).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun parseToc(zipFile: ZipFile, tocHref: String?, basePath: String): List<TocEntry> {
        if (tocHref == null) return emptyList()

        val tocEntry = zipFile.getEntry(tocHref) ?: return emptyList()

        return try {
            parseNcxToc(zipFile.getInputStream(tocEntry))
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseNcxToc(input: InputStream): List<TocEntry> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val entries = mutableListOf<TocEntry>()
        var currentTitle = ""
        var currentHref = ""

        var eventType = parser.eventType
        var inNavPoint = false
        var inText = false
        var inContent = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "navPoint" -> inNavPoint = true
                        "text" -> inText = true
                        "content" -> {
                            inContent = true
                            currentHref = parser.getAttributeValue(null, "src") ?: ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText && inNavPoint) {
                        currentTitle = parser.text?.trim() ?: ""
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "navPoint" -> {
                            if (currentTitle.isNotEmpty() && currentHref.isNotEmpty()) {
                                entries.add(TocEntry(currentTitle, currentHref, 0))
                            }
                            inNavPoint = false
                            currentTitle = ""
                            currentHref = ""
                        }
                        "text" -> inText = false
                        "content" -> inContent = false
                    }
                }
            }
            eventType = parser.next()
        }

        return entries
    }
}
