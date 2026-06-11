load("config.js");

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    if (url.startsWith("http")) return url.replace(/^http:\/\//i, "https://");
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function fetchDoc(url) {
    let response = fetch(url);
    if (response && response.ok) {
        return response.html();
    }

    if (response && (response.status === 400 || response.status === 401 || response.status === 403 || response.status === 503)) {
        let browser = Engine.newBrowser();
        browser.launch(url, 7000);
        let doc = browser.html();
        browser.close();
        return doc;
    }

    return null;
}

function normalizeEntities(text) {
    return (text || "")
        .replace(/&nbsp;/gi, " ")
        .replace(/&amp;/gi, "&")
        .replace(/&quot;/gi, "\"")
        .replace(/&#39;/gi, "'")
        .replace(/&lt;/gi, "<")
        .replace(/&gt;/gi, ">");
}

function formatChapterContent(rawHtml) {
    let text = rawHtml || "";

    text = text.replace(/<script[\s\S]*?<\/script>/gi, "");
    text = text.replace(/<style[\s\S]*?<\/style>/gi, "");
    text = text.replace(/<br\s*\/?>/gi, "\n");
    text = text.replace(/<\/(p|div|li|h[1-6])>/gi, "\n");
    text = text.replace(/<(p|div|li|h[1-6])[^>]*>/gi, "");
    text = text.replace(/<[^>]+>/g, " ");

    text = normalizeEntities(text);
    text = text.replace(/\r/g, "");
    text = text.replace(/[ \t]+\n/g, "\n");
    text = text.replace(/\n[ \t]+/g, "\n");
    text = text.replace(/[ \t]{2,}/g, " ");
    text = text.replace(/\n{2,}/g, "\n");
    text = text.trim();

    if (!text) return "";

    text = text.replace(/([.!?\u3002\uFF01\uFF1F\u2026]+["'\u201D\u2019)\]]*)\s+/g, "$1\n");
    text = text.replace(/\n{2,}/g, "\n");

    let lines = [];
    text.split("\n").forEach(line => {
        let t = (line || "").trim();
        if (t) lines.push(t);
    });

    return lines.join("<br/>");
}

function execute(url) {
    let doc = fetchDoc(normalizeUrl(url));
    if (!doc) {
        return Response.error("Khong the tai noi dung chuong.");
    }

    let contentEl = doc.select(".box-chap").first();
    if (!contentEl) contentEl = doc.select(".chapter-c-content .box-chap").first();
    if (!contentEl) {
        return Response.error("Khong tim thay noi dung chuong.");
    }

    let content = formatChapterContent(contentEl.html());
    if (!content) {
        return Response.error("Noi dung chuong trong.");
    }

    return Response.success(content);
}
