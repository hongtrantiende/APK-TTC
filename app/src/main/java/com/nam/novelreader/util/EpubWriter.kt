package com.nam.novelreader.util

import com.nam.novelreader.domain.model.Chapter
import com.nam.novelreader.domain.model.Novel
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Tiện ích hỗ trợ tạo tệp EPUB 2.0 / 3.0 cơ bản từ dữ liệu truyện chữ.
 */
object EpubWriter {

    fun writeEpub(
        outputStream: OutputStream,
        novel: Novel,
        chapters: List<Chapter>
    ) {
        ZipOutputStream(outputStream).use { zos ->
            // 1. mimetype (Must be first, no compression)
            val mimeEntry = ZipEntry("mimetype")
            mimeEntry.method = ZipEntry.STORED
            val mimeBytes = "application/epub+zip".toByteArray()
            mimeEntry.size = mimeBytes.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(mimeBytes)
            mimeEntry.crc = crc.value
            zos.putNextEntry(mimeEntry)
            zos.write(mimeBytes)
            zos.closeEntry()

            // 2. META-INF/container.xml
            val containerXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                    <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                    </rootfiles>
                </container>
            """.trimIndent()
            writeZipEntry(zos, "META-INF/container.xml", containerXml)

            // 3. OEBPS/content.opf
            val opfContent = buildContentOpf(novel, chapters)
            writeZipEntry(zos, "OEBPS/content.opf", opfContent)

            // 4. OEBPS/toc.ncx
            val ncxContent = buildTocNcx(novel, chapters)
            writeZipEntry(zos, "OEBPS/toc.ncx", ncxContent)

            // 5. OEBPS/css/style.css
            val cssContent = """
                body { margin: 1em; font-family: sans-serif; }
                h1, h2 { text-align: center; }
                p { line-height: 1.5; text-indent: 1.5em; margin-bottom: 0.5em; }
            """.trimIndent()
            writeZipEntry(zos, "OEBPS/css/style.css", cssContent)

            // 6. Cover page
            val coverHtml = """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
                <html xmlns="http://www.w3.org/1999/xhtml">
                <head>
                    <title>Cover</title>
                    <link rel="stylesheet" type="text/css" href="css/style.css"/>
                </head>
                <body>
                    <h1 style="text-align: center; margin-top: 20%;">${escapeXml(novel.title)}</h1>
                    <h3 style="text-align: center;">${escapeXml(novel.author)}</h3>
                </body>
                </html>
            """.trimIndent()
            writeZipEntry(zos, "OEBPS/cover.html", coverHtml)

            // 7. Chapters HTML
            chapters.forEachIndexed { index, chapter ->
                val html = buildChapterHtml(chapter)
                writeZipEntry(zos, "OEBPS/chapter_${index}.html", html)
            }
        }
    }

    private fun writeZipEntry(zos: ZipOutputStream, path: String, content: String) {
        zos.putNextEntry(ZipEntry(path))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun buildContentOpf(novel: Novel, chapters: List<Chapter>): String {
        val title = escapeXml(novel.title)
        val author = escapeXml(novel.author.ifBlank { "Unknown" })
        
        val manifestItems = StringBuilder()
        val spineItems = StringBuilder()

        chapters.forEachIndexed { index, _ ->
            manifestItems.appendLine("        <item id=\"chap_${index}\" href=\"chapter_${index}.html\" media-type=\"application/xhtml+xml\"/>")
            spineItems.appendLine("        <itemref idref=\"chap_${index}\"/>")
        }

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
                    <dc:title>${title}</dc:title>
                    <dc:creator opf:role="aut">${author}</dc:creator>
                    <dc:language>vi</dc:language>
                    <dc:identifier id="BookId">${novel.url.hashCode()}</dc:identifier>
                </metadata>
                <manifest>
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="css" href="css/style.css" media-type="text/css"/>
                    <item id="cover" href="cover.html" media-type="application/xhtml+xml"/>
${manifestItems.toString().trimEnd()}
                </manifest>
                <spine toc="ncx">
                    <itemref idref="cover"/>
${spineItems.toString().trimEnd()}
                </spine>
            </package>
        """.trimIndent()
    }

    private fun buildTocNcx(novel: Novel, chapters: List<Chapter>): String {
        val title = escapeXml(novel.title)
        
        val navPoints = StringBuilder()
        chapters.forEachIndexed { index, chapter ->
            val chapTitle = escapeXml(chapter.title.ifBlank { "Chương ${index + 1}" })
            navPoints.appendLine("""
                <navPoint id="navPoint-${index + 1}" playOrder="${index + 1}">
                    <navLabel><text>${chapTitle}</text></navLabel>
                    <content src="chapter_${index}.html"/>
                </navPoint>
            """.trimIndent().prependIndent("        "))
        }

        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN" "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
                <head>
                    <meta name="dtb:uid" content="${novel.url.hashCode()}"/>
                    <meta name="dtb:depth" content="1"/>
                    <meta name="dtb:totalPageCount" content="0"/>
                    <meta name="dtb:maxPageNumber" content="0"/>
                </head>
                <docTitle>
                    <text>${title}</text>
                </docTitle>
                <navMap>
${navPoints.toString().trimEnd()}
                </navMap>
            </ncx>
        """.trimIndent()
    }
    private fun buildChapterHtml(chapter: Chapter): String {
        val title = escapeXml(chapter.title)
        val cleanedContent = cleanHtmlContent(chapter.content)
        val paragraphs = cleanedContent.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val contentHtml = paragraphs.joinToString("\n") { "<p>${escapeXml(it)}</p>" }

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head>
                <title>${title}</title>
                <link rel="stylesheet" type="text/css" href="css/style.css"/>
            </head>
            <body>
                <h2>${title}</h2>
                $contentHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun cleanHtmlContent(html: String?): String {
        if (html.isNullOrBlank()) return ""
        var text = html
        text = text.replace(Regex("<!--[\\s\\S]*?-->"), "")
        text = text.replace(Regex("<script[^>]*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
        text = text.replace(Regex("<style[^>]*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
        
        // Dọn sạch các thực thể br dạng chuỗi html được escape trước và sau khi decode
        text = text.replace(Regex("&lt;br\\s*/?&gt;", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("</p>|</div>|</li>", RegexOption.IGNORE_CASE), "\n")
        text = text.replace(Regex("<p[^>]*?>|<div[^>]*?>|<li[^>]*?>", RegexOption.IGNORE_CASE), "")
        
        text = text.replace(Regex("<[^>]*?>"), "")
        
        text = text.replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&ldquo;", "“")
            .replace("&rdquo;", "”")
            .replace("&lsquo;", "‘")
            .replace("&rsquo;", "’")
        
        val lines = text.split("\n").map { it.trim() }
        val cleanedLines = mutableListOf<String>()
        for (line in lines) {
            if (line.isNotEmpty()) {
                cleanedLines.add(line)
            }
        }
        // Sử dụng \n\n để tạo dòng trống phân cách rõ ràng giữa các đoạn văn
        return cleanedLines.joinToString("\n\n")
    }
}
