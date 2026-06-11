load("config.js");

var CHAPTER_READMORE_RE = /本章尚未读完|点击下一页继续阅读|本章已阅读完毕/;
var CHAPTER_NOISE_RE = /Loading\.\.\.|内容未加载完成|刷新网页|关闭小说模式|关闭广告屏蔽|推荐使用【|收藏网址：/;
var MAX_CHAPTER_PARTS = 10;

function normalizeChapterUrl(url) {
    return stripUrlDecoration(resolveUrl(url)).replace(/_\d+\.html$/, ".html");
}

function fetchChapterDoc(url) {
    var res = fetchRetry(url);
    if (!res || !res.ok) return null;
    return res.html();
}

function replaceInlineImages(html) {
    return String(html || "").replace(
        /<img\b[^>]*?(?:src|_src|data-src|data-original)=["']([^"']*\/wzbodyimg\/[^"']+)["'][^>]*>/gi,
        function (match, src) {
            var ch = resolveWzBodyImageChar(src);
            return ch || match;
        }
    );
}

function removeReadMoreLines(content) {
    var paras = content.select("p");
    for (var i = 0; i < paras.size(); i++) {
        if (CHAPTER_READMORE_RE.test(cleanText(paras.get(i).text()))) {
            paras.get(i).remove();
        }
    }
}

function removeChapterNoise(content) {
    var scripts = content.select("script");
    for (var i = 0; i < scripts.size(); i++) {
        scripts.get(i).remove();
    }

    var paras = content.select("p");
    for (var pi = 0; pi < paras.size(); pi++) {
        if (CHAPTER_NOISE_RE.test(cleanText(paras.get(pi).text()))) {
            paras.get(pi).remove();
        }
    }
}

function getNextPartUrl(doc, baseUrl, currentUrl) {
    var links = doc.select("a[href]");
    var basePrefix = baseUrl.replace(/\.html$/, "");
    for (var i = 0; i < links.size(); i++) {
        var link = links.get(i);
        var text = cleanText(link.text());
        if (text !== "下一页") continue;

        var href = resolveUrl(link.attr("href") || "");
        if (!href || href === currentUrl) return "";
        if (href.indexOf(basePrefix) !== 0) return "";
        return href;
    }
    return "";
}

function execute(url) {
    var baseUrl = normalizeChapterUrl(url.indexOf("http") === 0 ? url : resolveUrl(url));
    var currentUrl = baseUrl;
    var parts = [];
    var seen = {};

    for (var part = 0; part < MAX_CHAPTER_PARTS && currentUrl && !seen[currentUrl]; part++) {
        seen[currentUrl] = true;

        var doc = fetchChapterDoc(currentUrl);
        if (!doc) {
            if (parts.length > 0) break;
            return Response.error("Khong tai duoc chuong");
        }

        var content = selFirst(doc, "#C0NTENT, .RBGsectionThree-content");
        if (!content) {
            if (parts.length > 0) break;
            return Response.error("Khong tim thay noi dung chuong");
        }

        removeReadMoreLines(content);
        removeChapterNoise(content);

        var html = replaceInlineImages(content.html());
        if (html) parts.push(html);

        currentUrl = getNextPartUrl(doc, baseUrl, currentUrl);
    }

    if (parts.length === 0) return Response.error("Khong doc duoc noi dung");
    return Response.success(parts.join("\n"));
}