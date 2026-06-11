load("config.js");

// Genre slug map for display names
var GENRE_MAP = {
    xuanhuan: "\u7384\u5e7b", xiuzhen: "\u4fee\u771f", dushi: "\u90fd\u5e02",
    lishi: "\u6b77\u53f2", wangyou: "\u7db2\u904a", kehuan: "\u79d1\u5e7b",
    nvpin: "\u5973\u983b", lingyi: "\u9748\u7570", tongren: "\u540c\u4eba",
    junshi: "\u8ecd\u4e8b", xuanyi: "\u61f8\u7591", chuanyue: "\u7a7f\u8d8a",
    other: "\u5176\u5b83"
};

function execute(url) {
    var storyUrl = resolveUrl(url);
    var doc = fetchCF(storyUrl);
    if (!doc) return Response.error("Lỗi tải trang truyện");

    // --- Name from h1.title ---
    var nameEl = selFirst(doc, "h1.title, h1, .title h1");
    var name = nameEl ? nameEl.text().trim() : "";
    if (!name) {
        var titleTag = selFirst(doc, "title");
        if (titleTag) {
            name = titleTag.text().trim().replace(/\s*[-_|].*$/, "").trim();
        }
    }

    // --- Cover image from #detail .cover img ---
    var cover = "";
    var picEl = selFirst(doc, "#detail .cover img, .media-left img, .bookimg img");
    if (picEl) {
        cover = picEl.attr("src") || picEl.attr("data-src") || "";
    }
    if (!cover) {
        var metaOg = selFirst(doc, "meta[property='og:image']");
        if (metaOg) cover = metaOg.attr("content") || "";
    }

    // --- Author from span.author or "作者：" pattern ---
    var author = "";
    var authorEl = selFirst(doc, ".meta span.author, .info span.author, span.author");
    if (authorEl) {
        author = authorEl.text().trim();
    }
    if (!author) {
        var metaEls = doc.select(".meta p, .meta span, .info p");
        for (var ai = 0; ai < metaEls.size(); ai++) {
            var atxt = metaEls.get(ai).text().trim();
            var am = atxt.match(/\u4f5c\u8005[\uff1a:]\s*(.+)/);  // 作者：
            if (am && am[1] && am[1].length < 50) {
                author = am[1].trim();
                break;
            }
        }
    }

    // --- Description from .intro ---
    var descEl = selFirst(doc, ".intro, #intro, .desc, .summary");
    var description = descEl ? stripHtml(descEl.html()) : "";

    // --- Status: 完結/連載 ---
    var ongoing = true;
    var bodyText = doc.text();
    if (bodyText.indexOf("\u5b8c\u7d50") !== -1 || bodyText.indexOf("\u5168\u672c") !== -1) {  // 完結, 全本
        ongoing = false;
    }

    // --- Genre from breadcrumb or category link ---
    var genres = [];
    var catLink = selFirst(doc, "a[href*='/bookstack/']");
    if (catLink) {
        var catHref = catLink.attr("href") || "";
        var catMatch = catHref.match(/\/bookstack\/([a-z]+)\//);
        if (catMatch && GENRE_MAP[catMatch[1]]) {
            genres.push({
                title: GENRE_MAP[catMatch[1]],
                input: catMatch[1],
                script: "genrecontent.js"
            });
        }
    }
    // Also scan for category in .meta
    var catEl = selFirst(doc, ".meta a[href*='bookstack']");
    if (catEl && genres.length === 0) {
        var catText = catEl.text().trim();
        if (catText) {
            genres.push({
                title: catText,
                input: storyUrl,
                script: "genrecontent.js"
            });
        }
    }

    // --- Suggests ---
    var suggests = [];
    // 編輯推薦 — editor recommendations from this detail page
    suggests.push({
        title: "\u7de8\u8f2f\u63a8\u85a6",  // 編輯推薦
        input: storyUrl,
        script: "suggest.js"
    });
    // Same author search
    if (author) {
        suggests.push({
            title: "\u540c\u4f5c\u8005: " + author,  // 同作者
            input: "author:" + author,
            script: "suggest.js"
        });
    }

    // --- Comments ---
    var bookIdMatch = storyUrl.match(/\/(\d{10,})\/?$/);
    var comments = [];
    if (bookIdMatch) {
        comments.push({
            title: "\u8a55\u8ad6",  // 評論
            input: bookIdMatch[1],
            script: "comment.js"
        });
    }

    var result = {
        name: name,
        cover: cover,
        host: HOST,
        author: author,
        description: description,
        detail: author ? "\u4f5c\u8005\uff1a" + author : "",  // 作者：
        ongoing: ongoing
    };
    if (genres.length > 0) result.genres = genres;
    if (suggests.length > 0) result.suggests = suggests;
    if (comments.length > 0) result.comments = comments;

    return Response.success(result);
}
