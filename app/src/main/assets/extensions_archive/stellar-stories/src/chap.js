load('config.js');

function contentFromApi(storySlug, chapterSlugText) {
    let data = apiData("/novels/" + encodeURIComponent(storySlug) + "/chapters/" + encodeURIComponent(chapterSlugText));
    if (!data) return "";
    let content = "";
    if (data.content && data.content.content) content = data.content.content;
    if (!content && data.content && data.content.paragraphs) {
        data.content.paragraphs.forEach(function(p) {
            if (p) content += "<p>" + p + "</p>";
        });
    }
    return stripBadChapterBlocks(content);
}

function decodeFlightString(text) {
    try {
        return JSON.parse("\"" + text + "\"");
    } catch (error) {
        return text.replace(/\\u003c/g, "<").replace(/\\u003e/g, ">").replace(/\\u0026/g, "&").replace(/\\n/g, "\n").replace(/\\"/g, "\"");
    }
}

function contentFromFlight(html) {
    if (!html) return "";
    let idMatch = /processedContent\\?"\s*:\s*\\?"\$([A-Za-z0-9]+)\\?"/.exec(html);
    if (!idMatch) return "";
    let marker = idMatch[1] + ":T";
    let start = html.indexOf(marker);
    if (start < 0) return "";
    let part = html.substring(start);
    let content = "";
    let re = /self\.__next_f\.push\(\[1,"([\s\S]*?)"\]\)<\/script>/g;
    let match;
    while ((match = re.exec(part)) !== null) {
        let decoded = decodeFlightString(match[1]);
        if (decoded.indexOf("<p") >= 0 || content) content += decoded;
        if (content && decoded.indexOf("processedContent") >= 0) break;
    }
    let first = content.indexOf("<p");
    if (first >= 0) content = content.substring(first);
    let last = content.lastIndexOf("</p>");
    if (last >= 0) content = content.substring(0, last + 4);
    return stripBadChapterBlocks(content);
}

function contentFromDoc(url) {
    let doc = getDoc(url);
    if (!doc) return "";
    let parts = [];
    doc.select(".novel-content p, article p, main p").forEach(function(p) {
        let text = cleanText(p.text());
        if (!text || foldText(text).indexOf("dang tai") >= 0) return;
        parts.push("<p>" + p.html() + "</p>");
    });
    if (parts.length > 0) return parts.join("\n");
    return contentFromFlight(doc.html ? doc.html() : "");
}

function execute(url) {
    let storySlug = storySlugFromUrl(url);
    let chSlug = chapterSlugFromUrl(url);
    if (!storySlug || !chSlug) return Response.error("Url chuong khong hop le.");
    let content = contentFromApi(storySlug, chSlug);
    if (!content) content = contentFromDoc(url);
    if (!content || cleanText(content).length < 20) return Response.error("Khong tim thay noi dung chuong.");
    return Response.success(content);
}
