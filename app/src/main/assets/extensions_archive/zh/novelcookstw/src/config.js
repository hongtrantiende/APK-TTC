var BASE_URL = "https://novel.cooks.tw";
var API_BASE = BASE_URL + "/api";

function fetchApi(path) {
    var sep = path.indexOf("?") >= 0 ? "&" : "?";
    var url = API_BASE + path + sep + "lang=zh-TW";
    var res = fetch(url);
    if (!res || !res.ok) return null;
    try { return res.json(); } catch(e) { return null; }
}

function coverUrl(id) {
    var n = parseInt(id);
    return "https://pic.cooks.tw/" + Math.floor(n / 1000) + "/" + n + "/" + n + "s.jpg";
}

function makeChapUrl(articleId, chapterId) {
    return BASE_URL + "/reader.html?articleid=" + articleId + "&chapterid=" + chapterId;
}

function parseArticleId(url) {
    var m = url.match(/[?&]articleid=(\d+)/);
    return m ? m[1] : null;
}

function parseChapterId(url) {
    var m = url.match(/[?&]chapterid=(\d+)/);
    return m ? m[1] : null;
}

function buildItems(items, limit) {
    var r = [], len = limit && limit < items.length ? limit : items.length;
    for (var i = 0; i < len; i++) {
        var n = items[i];
        r.push({
            name: n.articlename || "",
            link: BASE_URL + "/novel.html?articleid=" + n.articleid,
            host: BASE_URL,
            cover: coverUrl(n.articleid),
            description: n.intro || ""
        });
    }
    return r;
}

function textToHtml(text) {
    if (!text) return "";
    var lines = text.split(/\r?\n/);
    var html = "";
    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (line) html += "<p>" + line + "</p>";
    }
    return html;
}

