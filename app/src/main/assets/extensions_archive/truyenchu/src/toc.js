load("config.js");

var CHAP_HREF_RE = /\/truyen\/[^\/]+\/chuong/;

// Parse chapter links từ HTML fragment (static hoặc AJAX response)
function parseChapLinks(container, seen, out) {
    var links = container.select(".wp-manga-chapter a[href], li.wp-manga-chapter a[href]");
    for (var i = 0; i < links.size(); i++) {
        var a = links.get(i);
        var href = a.attr("href") || "";
        if (!href || !CHAP_HREF_RE.test(href)) continue;
        if (href.indexOf("http") !== 0) href = BASE_URL + href;
        if (seen[href]) continue;
        seen[href] = true;
        var name = a.text().trim();
        if (!name) continue;
        out.push({ name: name, url: href, host: HOST });
    }
}

function execute(url) {
    var storyUrl = resolveUrl(url);
    // Madara theme load chapters qua REST endpoint {storyUrl}ajax/chapters/
    var ajaxUrl = storyUrl.replace(/\/?$/, "/") + "ajax/chapters/";
    var ajaxRes = fetch(ajaxUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "Referer": storyUrl,
            "User-Agent": FETCH_HEADERS["User-Agent"]
        }
    });
    if (!ajaxRes || !ajaxRes.ok) return Response.error("Không tải được danh sách chương");
    var ajaxDoc = ajaxRes.html();
    if (!ajaxDoc) return Response.error("Không đọc được danh sách chương");

    var chapters = [];
    var seen = {};
    parseChapLinks(ajaxDoc, seen, chapters);

    if (chapters.length === 0) return Response.error("Không tìm thấy danh sách chương");

    // Danh sách trả về là mới nhất trước → đảo ngược về chương 1 trước
    var reversed = [];
    for (var j = chapters.length - 1; j >= 0; j--) {
        reversed.push(chapters[j]);
    }

    return Response.success(reversed);
}
