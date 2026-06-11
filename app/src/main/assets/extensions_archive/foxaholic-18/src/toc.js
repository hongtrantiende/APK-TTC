load("config.js");

var CHAPTER_NUMBER_RE = /(?:chapter|chap|ch)\s*(\d+(?:\.\d+)?)/i;
var LEADING_NUMBER_RE = /^\s*(\d+(?:\.\d+)?)\b/;
var PREMIUM_CHAPTER_ID_RE = /(?:^|\s)data-chapter-(\d+)(?:\s|$)/i;
var SYNTHETIC_PREMIUM_QUERY = "vbook_premium_chapter";
var CHAPTER_BLOCK_RE = /<li[^>]*class=["'][^"']*wp-manga-chapter[^"']*["'][^>]*>[\s\S]*?<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>[\s\S]*?<\/li>/gi;
var GENERIC_ANCHOR_RE = /<a[^>]*href=["']([^"']+)["'][^>]*>([\s\S]*?)<\/a>/gi;
var STORY_CHAPTER_URL_RE = /\/chapter-[^\/?#]+\/?(?:[?#].*)?$/i;
var ALT_STORY_CHAPTER_URL_RE = /\/(?:\d+|n-a)\/?(?:[?#].*)?$/i;
var LAST_AJAX_DEBUG = "";

function isChallengeDoc(doc) {
    if (!doc) return true;
    var titleEl = selFirst(doc, "title");
    var title = titleEl ? titleEl.text().trim() : "";
    if (title.indexOf("Just a moment") >= 0) return true;
    var text = doc.text() || "";
    return text.indexOf("security verification") >= 0 || text.indexOf("Checking your Browser") >= 0 || doc.select("iframe[title*='Cloudflare']").size() > 0;
}

function loadDoc(url) {
    var fullUrl = resolveUrl(url);
    var doc = null;
    var browser = Engine.newBrowser();
    try { doc = browser.launch(fullUrl, 15000); } catch (e) { doc = null; }
    try { browser.close(); } catch (e2) {}
    if (doc && !isChallengeDoc(doc)) return doc;

    var res = fetchRetry(fullUrl);
    if (!res || !res.ok) return null;
    try { doc = res.html(); } catch (e3) { doc = null; }
    return doc && !isChallengeDoc(doc) ? doc : null;
}

function loadBrowserDoc(url, timeout) {
    var fullUrl = resolveUrl(url);
    var browser = Engine.newBrowser();
    try {
        return browser.launch(fullUrl, timeout || 30000);
    } catch (e) {
        return null;
    } finally {
        try { browser.close(); } catch (e2) {}
    }
}

function makeBrowserPostScriptUrl(actionUrl) {
    var safeUrl = (actionUrl || "").replace(/'/g, "\\'");
    return "javascript:(function(){var f=document.createElement('form');f.method='POST';f.action='" + safeUrl + "';document.body.appendChild(f);f.submit();})();";
}

function makeBrowserPostDataUrl(actionUrl) {
    var safeUrl = (actionUrl || "").replace(/"/g, "&quot;");
    var html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>" +
        "<form id=\"vbook-post-form\" method=\"POST\" action=\"" + safeUrl + "\"></form>" +
        "<script>document.getElementById(\"vbook-post-form\").submit();<\/script>" +
        "</body></html>";
    return "data:text/html;charset=utf-8," + encodeURIComponent(html);
}

function loadChapterListWithBrowser(storyUrl, timeout) {
    var ajaxUrl = storyUrl.replace(/\/?$/, "/") + "ajax/chapters/?t=1";
    var browser = Engine.newBrowser();
    var doc = null;
    var t = timeout || 12000;
    try {
        // Warm story page once so cleantalk bot-detector can set _awl on this browser instance.
        try { browser.launch(storyUrl, 8000); } catch (e1) {}

        // Preferred path: submit a POST form from the same browser context that already has _awl.
        try { doc = browser.launch(makeBrowserPostScriptUrl(ajaxUrl), t); } catch (e2) { doc = null; }

        // Fallback: if javascript: navigation is unsupported, submit the same POST via a data URL page.
        if (!doc || isChallengeDoc(doc) || doc.select(".wp-manga-chapter, li.wp-manga-chapter").size() === 0) {
            try { doc = browser.launch(makeBrowserPostDataUrl(ajaxUrl), t); } catch (e3) { doc = null; }
        }

        return doc;
    } finally {
        try { browser.close(); } catch (e4) {}
    }
}

function loadChapterListDoc(storyUrl) {
    var ajaxUrl = storyUrl.replace(/\/?$/, "/") + "ajax/chapters/?t=1";
    var attempts = [
        {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "Referer": storyUrl,
                "User-Agent": FETCH_HEADERS["User-Agent"]
            }
        },
        {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": storyUrl,
                "User-Agent": FETCH_HEADERS["User-Agent"],
                "Accept": "text/html, */*; q=0.01"
            }
        },
        {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                "Referer": storyUrl,
                "User-Agent": FETCH_HEADERS["User-Agent"]
            },
            body: ""
        }
    ];

    LAST_AJAX_DEBUG = "";
    for (var i = 0; i < attempts.length; i++) {
        var options = attempts[i];
        var res = null;
        try {
            res = fetch(ajaxUrl, options);
        } catch (e1) {
            LAST_AJAX_DEBUG = "fetch throw at attempt " + (i + 1) + ": " + e1;
            continue;
        }

        if ((!res || !res.ok) && res && !(res.status >= 400 && res.status < 500)) {
            try { res = fetch(ajaxUrl, options); } catch (e2) {}
        }
        if (!res || !res.ok) {
            LAST_AJAX_DEBUG = "ajax status attempt " + (i + 1) + ": " + (res ? res.status : "null");
            continue;
        }

        try {
            var doc = res.html();
            if (doc) {
                LAST_AJAX_DEBUG = "ajax ok via html attempt " + (i + 1);
                return doc;
            }
            LAST_AJAX_DEBUG = "ajax html null attempt " + (i + 1);
        } catch (e3) {
            LAST_AJAX_DEBUG = "ajax html throw attempt " + (i + 1) + ": " + e3;
        }
    }

    return null;
}

function parseChapterHtml(rawHtml, storyUrl, seen, out) {
    if (!rawHtml) return;

    CHAPTER_BLOCK_RE.lastIndex = 0;
    while (true) {
        var match = CHAPTER_BLOCK_RE.exec(rawHtml);
        if (!match) break;

        var href = match[1] || "";
        if (!href || href.indexOf("javascript:") === 0 || href.charAt(0) === "#") continue;

        var name = (match[2] || "").replace(/<[^>]+>/g, " ").replace(/&nbsp;/gi, " ").replace(/\s+/g, " ").trim();
        if (!name) continue;

        href = resolveUrl(href);
        if (isVolumeEntry(name, href)) continue;
        if (href === storyUrl || seen[href]) continue;

        seen[href] = true;
        out.push({ name: name, url: href, host: HOST });
    }

    GENERIC_ANCHOR_RE.lastIndex = 0;
    while (true) {
        var anchorMatch = GENERIC_ANCHOR_RE.exec(rawHtml);
        if (!anchorMatch) break;

        var anchorHref = anchorMatch[1] || "";
        if (!isStoryChapterUrl(anchorHref, storyUrl)) continue;

        var fullHref = resolveUrl(anchorHref);
        var anchorName = cleanAnchorText(anchorMatch[2] || "");
        if (!anchorName || isVolumeEntry(anchorName, fullHref) || seen[fullHref]) continue;

        seen[fullHref] = true;
        out.push({ name: anchorName, url: fullHref, host: HOST });
    }
}

function cleanAnchorText(html) {
    return (html || "").replace(/<[^>]+>/g, " ").replace(/&nbsp;/gi, " ").replace(/\s+/g, " ").trim();
}

function isStoryChapterUrl(href, storyUrl) {
    if (!href) return false;
    var full = resolveUrl(href);
    var storyBase = storyUrl.replace(/\/?$/, "/");
    return full.indexOf(storyBase) === 0 && (STORY_CHAPTER_URL_RE.test(full) || ALT_STORY_CHAPTER_URL_RE.test(full));
}

function normalizeChapterTitle(name) {
    var text = (name || "").replace(/\s+/g, " ").trim();
    if (!text) return "";

    var splitIdx = text.lastIndexOf(" - ");
    if (splitIdx > 0) {
        var tail = text.substring(splitIdx + 3).trim();
        if (/^(chapter|\d+\s*-)/i.test(tail)) return tail;
    }

    return text;
}

function normalizeStoryUrl(url) {
    var storyUrl = resolveUrl(url);
    storyUrl = storyUrl.replace(new RegExp("[?&]" + SYNTHETIC_PREMIUM_QUERY + "=\\d+", "i"), "");
    storyUrl = storyUrl.replace(/[?&]$/, "");
    if (/\/chapter-[^\/]+\/?$/i.test(storyUrl)) {
        return storyUrl.replace(/\/chapter-[^\/]+\/?$/i, "/");
    }
    if (/\/(?:\d+|n-a)\/?$/i.test(storyUrl)) {
        return storyUrl.replace(/\/(?:\d+|n-a)\/?$/i, "/");
    }
    return storyUrl;
}

function extractPremiumChapterId(className) {
    var match = PREMIUM_CHAPTER_ID_RE.exec(className || "");
    return match ? match[1] : "";
}

function makePremiumChapterUrl(storyUrl, chapterId) {
    var baseUrl = storyUrl.replace(/\/?$/, "/");
    return baseUrl + "?" + SYNTHETIC_PREMIUM_QUERY + "=" + chapterId;
}

function isVolumeEntry(name, href) {
    if (!name) return false;
    if (/\bvolume\b/i.test(name) && /\/n-a\/?$/i.test(href || "")) return true;
    return /^\s*0\s*-\s*volume\b/i.test(name);
}

function parseChapterLinks(container, storyUrl, seen, out) {
    // Dùng .free-chap để chỉ lấy 35 free chapters, bỏ qua locked (không có class free-chap)
    var items = container.select(".wp-manga-chapter.free-chap, li.wp-manga-chapter.free-chap");
    if (!items || items.size() === 0) {
        // Fallback: lấy tất cả .wp-manga-chapter nếu site không dùng free-chap class
        items = container.select(".wp-manga-chapter, li.wp-manga-chapter");
    }
    for (var i = 0; i < items.size(); i++) {
        var item = items.get(i);
        var a = selFirst(item, "a");
        if (!a) continue;

        var name = a.text().replace(/\s+/g, " ").trim();
        if (!name) continue;

        var href = a.attr("href") || "";
        if (!href || href.indexOf("javascript:") === 0 || href.charAt(0) === "#") continue;
        href = resolveUrl(href);

        if (isVolumeEntry(name, href)) continue;

        if (href === storyUrl || seen[href]) continue;
        seen[href] = true;
        out.push({ name: name, url: href, host: HOST });
    }
}

function readChapterPage(url, storyUrl) {
    var doc = loadDoc(url);
    if (!doc) return null;

    var nameEl = selFirst(doc, "h1, .text-left h1, .reading-content h1, .entry-header h1");
    var name = nameEl ? normalizeChapterTitle(nameEl.text()) : "";
    if (!name) {
        var titleEl = selFirst(doc, "title");
        name = titleEl ? normalizeChapterTitle(titleEl.text().replace(/\s+[-|]\s*Foxaholic\s*18\s*$/i, "")) : "";
    }

    var prevEl = selFirst(doc, ".nav-previous a[href], .prev_page a[href], a.prev_page[href]");
    var nextEl = selFirst(doc, ".nav-next a[href], .next_page a[href], a.next_page[href]");
    var prevHref = prevEl ? (prevEl.attr("href") || "") : "";
    var nextHref = nextEl ? (nextEl.attr("href") || "") : "";

    if (prevHref && !isStoryChapterUrl(prevHref, storyUrl)) prevHref = "";
    if (nextHref && !isStoryChapterUrl(nextHref, storyUrl)) nextHref = "";

    return {
        name: name,
        url: resolveUrl(url),
        prev: prevHref ? resolveUrl(prevHref) : "",
        next: nextHref ? resolveUrl(nextHref) : ""
    };
}

function crawlFreeChapters(seedUrl, storyUrl) {
    if (!seedUrl) return [];

    var backSeen = {};
    var cursor = resolveUrl(seedUrl);
    var earliest = cursor;
    while (cursor && !backSeen[cursor]) {
        backSeen[cursor] = true;
        earliest = cursor;
        var backPage = readChapterPage(cursor, storyUrl);
        if (!backPage || !backPage.prev || backPage.prev === cursor) break;
        cursor = backPage.prev;
    }

    var out = [];
    var seen = {};
    cursor = earliest;
    var guard = 0;
    while (cursor && !seen[cursor] && guard < 300) {
        guard++;
        var page = readChapterPage(cursor, storyUrl);
        if (!page) break;
        seen[cursor] = true;
        if (page.name) out.push({ name: page.name, url: page.url, host: HOST });
        if (!page.next || page.next === cursor) break;
        cursor = page.next;
    }

    return out;
}

function getChapterNumber(name) {
    var match = CHAPTER_NUMBER_RE.exec(name || "");
    if (!match) match = LEADING_NUMBER_RE.exec(name || "");
    return match ? parseFloat(match[1]) : null;
}

function normalizeOrder(items) {
    if (!items || items.length < 2) return items;

    var firstNum = getChapterNumber(items[0].name);
    var lastNum = getChapterNumber(items[items.length - 1].name);
    if (firstNum != null && lastNum != null && firstNum < lastNum) return items;

    var reversed = [];
    for (var i = items.length - 1; i >= 0; i--) reversed.push(items[i]);
    return reversed;
}

function execute(url) {
    var inputUrl = resolveUrl(url);
    var storyUrl = normalizeStoryUrl(inputUrl);
    var seedChapterUrl = isStoryChapterUrl(inputUrl, storyUrl) ? inputUrl : "";
    var chapters = [];
    var seen = {};
    var hasAjaxChapters = false;
    var ajaxUrl = storyUrl.replace(/\/?$/, "/") + "ajax/chapters/?t=1";

    var chapterListDoc = loadChapterListWithBrowser(storyUrl, 12000);
    if (!chapterListDoc) {
        LAST_AJAX_DEBUG = "browser-post-null";
    } else if (isChallengeDoc(chapterListDoc)) {
        LAST_AJAX_DEBUG = "browser-post-challenge";
    } else {
        parseChapterLinks(chapterListDoc, storyUrl, seen, chapters);
        LAST_AJAX_DEBUG = "browser-post-links:" + chapters.length;
        if (chapters.length === 0) {
            try { parseChapterHtml(chapterListDoc.html(), storyUrl, seen, chapters); } catch (e0) {}
            LAST_AJAX_DEBUG = "browser-post-html:" + chapters.length;
        }
        if (chapters.length > 0) {
            hasAjaxChapters = true;
            LAST_AJAX_DEBUG = "browser-post-ok:" + chapters.length;
        }
    }

    // Fallback: fetch ajax/chapters/ trực tiếp (WebView cookie store có _awl từ browser trên)
    if (chapters.length === 0) {
        var ajaxOpts = [
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": storyUrl,
                    "User-Agent": FETCH_HEADERS["User-Agent"],
                    "Accept": "text/html, */*; q=0.01"
                }
            },
            {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "Referer": storyUrl,
                    "User-Agent": FETCH_HEADERS["User-Agent"]
                },
                body: ""
            }
        ];
        for (var ai = 0; ai < ajaxOpts.length && chapters.length === 0; ai++) {
            var ajaxRes = null;
            try { ajaxRes = fetch(ajaxUrl, ajaxOpts[ai]); } catch (eA) { ajaxRes = null; }
            if (!ajaxRes) { LAST_AJAX_DEBUG = "ajax null attempt " + (ai + 1); continue; }
            if (!ajaxRes.ok) { LAST_AJAX_DEBUG = "ajax status " + ajaxRes.status + " attempt " + (ai + 1); continue; }
            var ajaxDoc = null;
            try { ajaxDoc = ajaxRes.html(); } catch (eH) {}
            if (ajaxDoc) {
                parseChapterLinks(ajaxDoc, storyUrl, seen, chapters);
            }
            if (chapters.length > 0) {
                hasAjaxChapters = true;
                LAST_AJAX_DEBUG = "ajax ok attempt " + (ai + 1);
            }
        }
    }

    // Bước 3: Crawl từ seed chapter URL nếu input là chapter URL
    if (chapters.length === 0 && seedChapterUrl) {
        var seededChapters = crawlFreeChapters(seedChapterUrl, storyUrl);
        if (seededChapters.length > 0) {
            chapters = seededChapters;
            LAST_AJAX_DEBUG = (LAST_AJAX_DEBUG ? LAST_AJAX_DEBUG + " | " : "") + "chapter-seed-crawl";
        }
    }

    if (chapters.length === 0) {
        return Response.error("Không tìm thấy free chapter | " + LAST_AJAX_DEBUG);
    }

    var validChapters = [];
    for (var i = 0; i < chapters.length; i++) {
        var c = chapters[i];
        if (!c || typeof c !== "object") continue;
        if (!c.name || typeof c.name !== "string" || !c.url || typeof c.url !== "string") continue;
        if (!c.host || typeof c.host !== "string") continue;
        validChapters.push({ name: c.name, url: c.url, host: c.host });
    }
    if (validChapters.length === 0) return Response.error("Không có chương hợp lệ (format lỗi)");
    return Response.success(normalizeOrder(validChapters));
}