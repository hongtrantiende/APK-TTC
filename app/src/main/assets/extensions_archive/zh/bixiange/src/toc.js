load("config.js");

// Precompile regex — dùng trong loop
var INDEX_PAGE_RE = /\/index_(\d+)\.html/;

// Regex nhận diện text là ký tự UI toggle — không phải tên chương thật
var UI_TOGGLE_RE = /^[+\-×✕▸▾▼▲◆□■●○\s]{1,4}$|^(展开|收起|更多|显示|隐藏)$/;
// Nhãn điều hướng — bỏ qua
var SKIP_NAME_RE = /^在线阅读$|^目录$|^上一章$|^下一章$|^上一页$|^下一页$/;

// Trích link chương từ 1 trang doc — push vào chapters/seen, trả về số chương đã thêm
function extractChaps(doc, storyPath, chapters, seen) {
    var chapLinks = doc.select("a[href*='" + storyPath + "/']");
    var added = 0;
    for (var i = 0; i < chapLinks.size(); i++) {
        var a = chapLinks.get(i);
        var chapInfo = getChapterHrefInfo(a.attr("href") || "", storyPath);
        if (!chapInfo || chapInfo.part !== 1) continue;

        var href = resolveUrl(chapInfo.path);

        var chapName = a.text().trim();
        if (!chapName || UI_TOGGLE_RE.test(chapName)) {
            chapName = a.attr("title") || "";
            if (!chapName || UI_TOGGLE_RE.test(chapName)) continue;
        }
        if (SKIP_NAME_RE.test(chapName)) continue;
        if (chapName.length < 2) continue;
        if (seen[href]) continue;

        seen[href] = true;
        chapters.push({ number: chapInfo.number, title: chapName, url: href, host: HOST });
        added++;
    }
    return added;
}

function execute(url) {
    var storyUrl = ensureStoryUrl(url);
    var doc = fetchBrowser(storyUrl, 7000);
    if (!doc) return Response.error("Lỗi tải mục lục");

    var storyPath = storyPathFromUrl(storyUrl);

    var chapters = [];
    var seen = {};

    // Trang đầu
    extractChaps(doc, storyPath, chapters, seen);

    // Phát hiện phân trang TOC — bixiange DedeCMS dùng /storyPath/index_N.html
    var maxPage = 1;
    var pageLinks = doc.select("a[href*='" + storyPath + "/index_']");
    for (var pi = 0; pi < pageLinks.size(); pi++) {
        var ph = pageLinks.get(pi).attr("href") || "";
        var pm = INDEX_PAGE_RE.exec(ph);
        if (pm) {
            var pn = parseInt(pm[1], 10);
            if (pn > maxPage) maxPage = pn;
        }
    }

    for (var p = 2; p <= maxPage && p <= 50; p++) {
        var pageUrl = BASE_URL + storyPath + "/index_" + p + ".html";
        var pageDoc = fetchBrowser(pageUrl, 7000);
        if (!pageDoc) break;
        extractChaps(pageDoc, storyPath, chapters, seen);
    }

    if (chapters.length === 0) return Response.error("Không tìm thấy danh sách chương");

    chapters.sort(function(a, b) {
        return a.number - b.number;
    });

    var result = [];
    for (var i = 0; i < chapters.length; i++) {
        result.push({
            name: "Chương " + chapters[i].number + ": " + chapters[i].title,
            url: chapters[i].url,
            host: HOST
        });
    }

    return Response.success(result);
}
