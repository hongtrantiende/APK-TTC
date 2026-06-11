load("config.js");

function shouldSkipLine(line) {
    let text = cleanText(line || "");
    if (!text) return true;
    return text.indexOf("返回目录") >= 0 ||
        text.indexOf("返回目錄") >= 0 ||
        text.indexOf("加入书签") >= 0 ||
        text.indexOf("加入書籤") >= 0 ||
        text.indexOf("上一页") >= 0 ||
        text.indexOf("下一页") >= 0 ||
        text.indexOf("首頁") >= 0 ||
        text.indexOf("书架") >= 0 ||
        text.indexOf("書架") >= 0;
}

function contentHtml(node) {
    let parts = [];
    let ps = node.select("p");
    if (ps.size() > 0) {
        for (let i = 0; i < ps.size(); i++) {
            let text = cleanText(ps.get(i).text());
            if (!text || shouldSkipLine(text)) continue;
            parts.push("<p>" + escapeHtml(text) + "</p>");
        }
        if (parts.length > 0) return parts.join("\n");
    }

    let raw = rawHtmlToText(node.html());
    let lines = raw.split("\n");
    let kept = [];
    for (let i = 0; i < lines.length; i++) {
        if (!shouldSkipLine(lines[i])) kept.push(lines[i]);
    }
    return textToHtml(kept.join("\n"));
}

function execute(url) {
    if (!isChapterUrl(url)) return Response.error("Url chuong khong hop le.");

    let session = openBrowserSession(url);
    if (!session) return loadError();

    let doc = null;
    try {
        doc = session.doc && !isChallengeDoc(session.doc) ? session.doc : browserFetchDoc(session, url);
    } finally {
        closeBrowserSession(session);
    }
    if (!doc) return loadError();

    let title = firstText(doc, ["#nr_title", ".nr_title", "h1"]);
    let node = doc.select("#nr1, #chaptercontent, .chaptercontent, #nr").first();
    if (!node) return Response.error("Khong tim thay noi dung chuong.");

    let html = contentHtml(node);
    if (!html || cleanText(html).length < 20) return Response.error("Khong tim thay noi dung chuong.");
    return Response.success((title ? "<h2>" + escapeHtml(title) + "</h2>\n" : "") + html);
}
