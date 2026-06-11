load("config.js");

var CHAP_CSS = "#mycontent, #BookText, .booktext, #nr, .nr, .content, #content, #chaptercontent, .chapter-content";
var SEPARATOR_RE = /^[^\u4e00-\u9fff\u3400-\u4dbf\w\d]+$/;
var PROMO_LINE_RE = /本书由|提供下载|最新章节|手机用户|一秒钟记住|请记住|本站网址|章节错误|内容更新|本章字数|正文字数|分享本书|点击右上角|阅读全文|www\.|\.net|\.com|\.me|bixiange|笔仙阁|全文免费|免费阅读|作者有话|下载地址|最新站点/i;

function addIndent(text) {
    var lines = text.split("\n");
    var out = [];
    var prevContent = false;

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        var trimmed = line.replace(/^[ \t]+/, "").replace(/[ \t]+$/, "");
        if (!trimmed) {
            if (prevContent) {
                out.push("");
                prevContent = false;
            }
            continue;
        }
        if (PROMO_LINE_RE.test(trimmed)) continue;
        if (SEPARATOR_RE.test(trimmed)) {
            out.push(trimmed);
            prevContent = false;
            continue;
        }
        if (trimmed.charAt(0) === "\u3010") {
            if (prevContent) out.push("");
            out.push(trimmed);
            prevContent = true;
            continue;
        }
        if (trimmed.length <= 5) {
            if (prevContent) out.push("");
            out.push(trimmed);
            prevContent = true;
            continue;
        }
        if (prevContent) out.push("");
        out.push(trimmed.charAt(0) === "\u3000" ? trimmed : "\u3000\u3000" + trimmed);
        prevContent = true;
    }

    return out.join("\n").replace(/\n{3,}/g, "\n\n").trim();
}

function removeNoise(doc) {
    doc.select("script, style, ins, iframe, .advert, .advertisement, .chapter-footer, .chapter-header, .readBtn, .bottem, .bottom, .top, .chapter-nav, .read-nav").remove();
    doc.select("[class*=' ads'], [class*='ads '], [class$=ads], [class^=ads], [id*=' ads'], [id*='ads '], [id$=ads], [id^=ads]").remove();
}

function detectChapPages(doc, chapPath) {
    var maxPage = 1;
    var links = doc.select("a[href*='" + chapPath + "_']");
    for (var i = 0; i < links.size(); i++) {
        var href = links.get(i).attr("href") || "";
        var match = /_(\d+)\.html/.exec(href);
        if (match) {
            var pageNum = parseInt(match[1], 10);
            if (pageNum > maxPage) maxPage = pageNum;
        }
    }
    return maxPage;
}

function findContent(doc) {
    removeNoise(doc);

    var el = selFirst(doc, CHAP_CSS);
    if (el) {
        el.select("a").remove();

        var paras = el.select("p");
        if (paras.size() > 2) {
            var parts = [];
            for (var i = 0; i < paras.size(); i++) {
                var ptxt = paras.get(i).text().trim().replace(/^\u3000+/, "");
                if (!ptxt || PROMO_LINE_RE.test(ptxt)) continue;
                parts.push("<p>\u3000\u3000" + ptxt + "</p>");
            }
            if (parts.length > 0) {
                var html = parts.join("\n");
                if (html.length > 200) return html;
            }
        }

        var txt = addIndent(stripHtml(el.html()));
        if (txt.length > 100) return txt;
    }

    var divs = doc.select("div");
    var best = null;
    var bestLen = 200;
    for (var di = 0; di < divs.size(); di++) {
        var div = divs.get(di);
        var textLen = div.text().length;
        if (textLen <= bestLen) continue;
        var linkLen = div.select("a").text().length;
        if (linkLen > textLen * 0.4) continue;
        bestLen = textLen;
        best = div;
    }

    return best ? addIndent(stripHtml(best.html())) : "";
}

function execute(url) {
    var chapUrl = resolveUrl(url);
    var chapPath = chapUrl.replace(BASE_URL, "").replace(/\.html$/, "").replace(/_\d+$/, "");

    var doc = fetchBrowser(chapUrl, 7000);
    if (!doc) {
        var res = fetchRetry(chapUrl);
        if (!res || !res.ok) return Response.error("Không thể tải chương");
        doc = res.html();
        if (!doc) return Response.error("Không thể tải chương");
    }

    var maxPage = detectChapPages(doc, chapPath);
    var content = findContent(doc);

    for (var p = 2; p <= maxPage && p <= 5; p++) {
        var pageUrl = BASE_URL + chapPath + "_" + p + ".html";
        var pageDoc = fetchBrowser(pageUrl, 8000);
        if (!pageDoc) break;
        var pageContent = findContent(pageDoc);
        if (!pageContent) break;
        content += "\n" + pageContent;
    }

    if (!content || content.length < 50) return Response.error("Không tìm thấy nội dung chương");
    return Response.success(content);
}
