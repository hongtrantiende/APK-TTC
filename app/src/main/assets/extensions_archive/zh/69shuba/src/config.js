let BASE_URL = "https://69shuba.tw";
let LAST_ERROR = "";
let DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";
let ANDROID_UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36";

let CATEGORY_ITEMS = [
    {title: "玄幻魔法", slug: "xuanhuan"},
    {title: "武侠修真", slug: "wuxia"},
    {title: "都市言情", slug: "dushi"},
    {title: "历史军事", slug: "lishi"},
    {title: "游戏竞技", slug: "youxi"},
    {title: "科幻灵异", slug: "kehu"},
    {title: "言情小说", slug: "yanqing"}
];

try {
    if (CONFIG_URL) BASE_URL = ("" + CONFIG_URL).replace(/\/+$/, "");
} catch (error) {
}

BASE_URL = BASE_URL.replace(/\/+$/, "");

function cleanText(text) {
    if (text === null || text === undefined) return "";
    return ("" + text).replace(/\s+/g, " ").trim();
}

function foldText(text) {
    text = cleanText(text).toLowerCase();
    try {
        text = text.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    } catch (error) {
    }
    return text.replace(/\u0111/g, "d").replace(/\u0110/g, "d");
}

function stripTrailingSlash(url) {
    if (!url) return "";
    return ("" + url).replace(/\/+$/, "");
}

function normalizeUrl(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (!url || /^javascript:/i.test(url) || url === "#") return "";
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.indexOf("/") === 0) return BASE_URL + url;
    if (!/^https?:\/\//i.test(url)) return BASE_URL + "/" + url.replace(/^\/+/, "");
    return url.replace(/^https?:\/\/(?:www\.)?69shuba\.tw/i, BASE_URL);
}

function normalizeImage(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (!url || url.indexOf("data:image") === 0) return "";
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.indexOf("/") === 0) return BASE_URL + url;
    if (!/^https?:\/\//i.test(url)) return BASE_URL + "/" + url.replace(/^\/+/, "");
    return url.replace(/^http:\/\//i, "https://").replace(/^https?:\/\/(?:www\.)?69shuba\.tw/i, BASE_URL);
}

function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    return ("" + text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

function defaultHeaders(options) {
    let headers = {};
    if (options && options.headers) {
        for (let key in options.headers) headers[key] = options.headers[key];
    }
    headers["Accept"] = headers["Accept"] || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    headers["Accept-Language"] = headers["Accept-Language"] || "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";
    headers["Referer"] = headers["Referer"] || BASE_URL + "/";
    headers["User-Agent"] = headers["User-Agent"] || DESKTOP_UA;
    return headers;
}

function request(url, options) {
    options = options || {};
    options.headers = defaultHeaders(options);
    return fetch(normalizeUrl(url), options);
}

function isChallengeDoc(doc) {
    if (!doc) return true;
    let html = doc.html ? doc.html() : "";
    let title = cleanText(doc.select("title").text());
    return title.indexOf("Please complete human verification") >= 0 ||
        title.indexOf("Just a moment") >= 0 ||
        html.indexOf("cf-turnstile") >= 0 ||
        html.indexOf("aegis_captcha_verify") >= 0 ||
        html.indexOf("human verification") >= 0;
}

function fetchDocDirect(url, options) {
    LAST_ERROR = "";
    try {
        let response = request(url, options);
        if (response && response.ok) {
            let doc = response.html();
            if (!isChallengeDoc(doc)) return doc;
            LAST_ERROR = "69ShuBa yeu cau xac minh nguoi dung.";
        } else if (response) {
            LAST_ERROR = "HTTP " + response.status;
        }
    } catch (error) {
        LAST_ERROR = "Khong tai duoc 69ShuBa: " + error;
    }
    return null;
}

function jsString(value) {
    try {
        return JSON.stringify(value === null || value === undefined ? "" : value);
    } catch (error) {
        return "\"\"";
    }
}

function openBrowserSession(url) {
    let browser = null;
    let target = normalizeUrl(url || BASE_URL + "/");
    try {
        browser = Engine.newBrowser();
        try {
            browser.setUserAgent(ANDROID_UA);
        } catch (uaError) {
            try {
                browser.setUserAgent(UserAgent.android());
            } catch (error) {
            }
        }
        let doc = browser.launch(target, 60000);
        for (let i = 0; i < 18 && isChallengeDoc(doc); i++) {
            sleep(3000);
            try {
                doc = browser.html();
            } catch (htmlError) {
            }
        }
        if (!isChallengeDoc(doc)) {
            return {
                browser: browser,
                doc: doc,
                url: target
            };
        }
        LAST_ERROR = "69ShuBa can xac minh. Hay tick captcha trong cua so trinh duyet ext.";
    } catch (error) {
        LAST_ERROR = "Khong mo duoc trinh duyet 69ShuBa: " + error;
    }
    try {
        if (browser) browser.close();
    } catch (closeError) {
    }
    return null;
}

function closeBrowserSession(session) {
    try {
        if (session && session.browser) session.browser.close();
    } catch (error) {
    }
}

function browserNavigateDoc(session, url) {
    let browser = session && session.browser ? session.browser : session;
    if (!browser) return null;

    let target = normalizeUrl(url || BASE_URL + "/");
    try {
        let doc = browser.launch(target, 60000);
        for (let i = 0; i < 18 && isChallengeDoc(doc); i++) {
            sleep(3000);
            try {
                doc = browser.html();
            } catch (htmlError) {
            }
        }
        if (!isChallengeDoc(doc)) {
            if (session && session.browser) {
                session.doc = doc;
                session.url = target;
            }
            return doc;
        }
        LAST_ERROR = "69ShuBa can xac minh. Hay tick captcha trong cua so trinh duyet ext.";
    } catch (error) {
        LAST_ERROR = "Khong mo duoc trang 69ShuBa: " + error;
    }
    return null;
}

function browserFetchResult(session, url, options) {
    let browser = session && session.browser ? session.browser : session;
    if (!browser) return null;

    let method = cleanText(options && options.method ? options.method : "GET") || "GET";
    let headers = defaultHeaders(options);
    let body = options && options.body !== undefined ? options.body : null;
    let key = "__vbook69_" + new Date().getTime() + "_" + Math.floor(Math.random() * 1000);
    let script = "(function(){var k=" + jsString(key) +
        ";var headers=JSON.parse(" + jsString(JSON.stringify(headers)) + ");window[k]='';" +
        "fetch(" + jsString(normalizeUrl(url)) + ",{method:" + jsString(method) + ",credentials:'include',headers:headers" +
        (body !== null ? ",body:" + jsString(body) : "") +
        "}).then(function(r){return r.text().then(function(t){window[k]=JSON.stringify({status:r.status,url:r.url,text:t});});}).catch(function(e){window[k]=JSON.stringify({error:String(e)});});return 'ok';})()";
    try {
        browser.callJs(script, 3000);
    } catch (error) {
        LAST_ERROR = "Browser fetch loi: " + error;
        return null;
    }

    for (let i = 0; i < 30; i++) {
        sleep(500);
        try {
            let raw = browser.callJs("(function(){return window[" + jsString(key) + "]||'';})()", 3000);
            if (!raw) continue;
            return JSON.parse(raw);
        } catch (error) {
        }
    }
    LAST_ERROR = "Browser fetch 69ShuBa bi timeout.";
    return null;
}

function browserFetchDoc(session, url, options) {
    let result = browserFetchResult(session, url, options);
    let method = cleanText(options && options.method ? options.method : "GET").toUpperCase() || "GET";
    if (!result) {
        if (method === "GET") return browserNavigateDoc(session, url);
        return null;
    }
    if (result.error) {
        LAST_ERROR = result.error;
        if (method === "GET") return browserNavigateDoc(session, url);
        return null;
    }
    let doc = Html.parse(result.text || "");
    if (result.status >= 200 && result.status < 300 && !isChallengeDoc(doc)) return doc;
    if (isChallengeDoc(doc)) {
        LAST_ERROR = "69ShuBa can xac minh. Hay tick captcha trong cua so trinh duyet ext.";
    } else {
        LAST_ERROR = "HTTP " + result.status;
    }
    if (method === "GET") return browserNavigateDoc(session, url);
    return null;
}

function getDoc(url, session) {
    url = normalizeUrl(url);
    if (session) {
        if (session.doc && normalizeUrl(session.url) === url && !isChallengeDoc(session.doc)) return session.doc;
        return browserFetchDoc(session, url);
    }

    let doc = fetchDocDirect(url);
    if (doc) return doc;

    let opened = openBrowserSession(url);
    if (!opened) return null;
    try {
        if (opened.doc && !isChallengeDoc(opened.doc) && normalizeUrl(opened.url) === url) return opened.doc;
        return browserFetchDoc(opened, url);
    } finally {
        closeBrowserSession(opened);
    }
}

function postDoc(url, body, session) {
    let options = {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": BASE_URL,
            "Referer": BASE_URL + "/search/"
        },
        body: body
    };

    if (session) return browserFetchDoc(session, url, options);

    let doc = fetchDocDirect(url, options);
    if (doc) return doc;

    let opened = openBrowserSession(BASE_URL + "/");
    if (!opened) return null;
    try {
        return browserFetchDoc(opened, url, options);
    } finally {
        closeBrowserSession(opened);
    }
}

function loadError(message) {
    return Response.error(message || LAST_ERROR || "Khong tai duoc 69ShuBa.");
}

function pathOf(url) {
    url = normalizeUrl(url);
    let match = /^https?:\/\/[^/]+(\/[^?#]*)?/i.exec(url);
    return match && match[1] ? match[1] : "/";
}

function queryParam(url, name) {
    let re = new RegExp("[?&]" + name + "=([^&#]+)", "i");
    let match = re.exec(url || "");
    if (!match) return "";
    try {
        return decodeURIComponent(match[1].replace(/\+/g, " "));
    } catch (error) {
        return match[1];
    }
}

function addQuery(url, key, value) {
    if (value === null || value === undefined || value === "") return url;
    let parts = (url || "").split("?");
    let base = parts[0];
    let pairs = parts.length > 1 ? parts[1].split("&") : [];
    let out = [];
    let found = false;
    for (let i = 0; i < pairs.length; i++) {
        if (!pairs[i]) continue;
        let name = pairs[i].split("=")[0];
        if (name === key) {
            out.push(key + "=" + encodeURIComponent(value));
            found = true;
        } else {
            out.push(pairs[i]);
        }
    }
    if (!found) out.push(key + "=" + encodeURIComponent(value));
    return base + "?" + out.join("&");
}

function isStoryUrl(url) {
    return /^\/book\/\d+\/?$/i.test(pathOf(url));
}

function isTocUrl(url) {
    return /^\/indexlist\/\d+(?:\/\d+)?\/?$/i.test(pathOf(url));
}

function isChapterUrl(url) {
    return /^\/read\/\d+\/\d+\/?$/i.test(pathOf(url));
}

function isCategoryUrl(url) {
    return /^\/fenlei\/[^/?#]+\/\d+\/?$/i.test(pathOf(url));
}

function isSearchUrl(url) {
    return /^\/search(?:\/\d+)?\/?$/i.test(pathOf(url));
}

function storyIdFromUrl(url) {
    let match = /\/(?:book|indexlist|read)\/(\d+)/i.exec(pathOf(url));
    return match ? match[1] : "";
}

function chapterIdFromUrl(url) {
    let match = /\/read\/\d+\/(\d+)/i.exec(pathOf(url));
    return match ? match[1] : "";
}

function categorySlugFromUrl(url) {
    let match = /\/fenlei\/([^/]+)\//i.exec(pathOf(url));
    return match ? match[1] : "";
}

function storyUrl(bookId) {
    return BASE_URL + "/book/" + bookId + "/";
}

function tocUrl(bookId, page) {
    let number = parseInt(page || "1", 10);
    if (!number || number < 2) return BASE_URL + "/indexlist/" + bookId + "/";
    return BASE_URL + "/indexlist/" + bookId + "/" + number + "/";
}

function chapterUrl(bookId, chapterId) {
    return BASE_URL + "/read/" + bookId + "/" + chapterId;
}

function storyUrlFromAny(url) {
    let bid = storyIdFromUrl(url);
    return bid ? storyUrl(bid) : normalizeUrl(url);
}

function tocUrlFromAny(url) {
    let bid = storyIdFromUrl(url);
    return bid ? tocUrl(bid, pageNumberFromUrl(url)) : normalizeUrl(url);
}

function categoryUrl(slug, page) {
    return BASE_URL + "/fenlei/" + slug + "/" + (page || 1) + "/";
}

function pageNumberFromUrl(url) {
    let path = pathOf(url);
    let match = /\/fenlei\/[^/]+\/(\d+)\/?$/i.exec(path);
    if (match) return parseInt(match[1], 10);
    match = /\/indexlist\/\d+\/(\d+)\/?$/i.exec(path);
    if (match) return parseInt(match[1], 10);
    match = /\/search\/(\d+)\/?$/i.exec(path);
    if (match) return parseInt(match[1], 10);
    match = /[?&]page=(\d+)/i.exec(url || "");
    return match ? parseInt(match[1], 10) : 1;
}

function listPageUrl(url, page) {
    let target = normalizeUrl(url || BASE_URL + "/");
    if (!page || page === "1") return target;
    if (("" + page).indexOf("http") === 0 || ("" + page).indexOf("/") === 0) return normalizeUrl(page);
    if (isCategoryUrl(target)) return categoryUrl(categorySlugFromUrl(target), parseInt(page, 10) || 1);
    if (isTocUrl(target)) return tocUrl(storyIdFromUrl(target), parseInt(page, 10) || 1);
    return addQuery(target, "page", page);
}

function nextPage(doc, currentUrl, pathPattern) {
    let nextNode = doc.select("a.index-container-btn[href], a[rel=next], .pagination a.next").first();
    if (nextNode) {
        let href = normalizeUrl(nextNode.attr("href"));
        if (href && (!pathPattern || href.indexOf(pathPattern) >= 0)) return href;
    }

    let current = pageNumberFromUrl(currentUrl || "");
    let links = doc.select("a[href]");
    let bestPage = 0;
    let bestHref = "";
    for (let i = 0; i < links.size(); i++) {
        let href = normalizeUrl(links.get(i).attr("href"));
        if (!href || href === normalizeUrl(currentUrl)) continue;
        if (pathPattern && href.indexOf(pathPattern) < 0) continue;
        let page = pageNumberFromUrl(href);
        if (page > current && (!bestPage || page < bestPage)) {
            bestPage = page;
            bestHref = href;
        }
    }
    return bestHref;
}

function firstText(node, selectors) {
    for (let i = 0; i < selectors.length; i++) {
        let e = node.select(selectors[i]).first();
        let text = e ? cleanText(e.text()) : "";
        if (text) return text;
    }
    return "";
}

function firstHtml(node, selectors) {
    for (let i = 0; i < selectors.length; i++) {
        let e = node.select(selectors[i]).first();
        if (e && cleanText(e.text())) return e.html();
    }
    return "";
}

function metaContent(doc, selector) {
    let node = doc.select(selector).first();
    return node ? cleanText(node.attr("content")) : "";
}

function metaImage(doc) {
    return normalizeImage(metaContent(doc, "meta[property=og:image], meta[name=twitter:image]"));
}

function imageAttr(e) {
    if (!e) return "";
    let srcset = e.attr("data-srcset") || e.attr("srcset");
    if (srcset) return cleanText(srcset.split(",")[0].split(" ")[0]);
    return e.attr("data-src") || e.attr("data-original") || e.attr("data-lazy-src") || e.attr("src");
}

function imageFromNode(e) {
    return normalizeImage(imageAttr(e));
}

function lineNodes(doc) {
    return doc.select("p, li, dd, dt, span, div");
}

function textByPrefix(doc, labels) {
    let nodes = lineNodes(doc);
    for (let i = 0; i < nodes.size(); i++) {
        let text = cleanText(nodes.get(i).text());
        let folded = foldText(text);
        for (let j = 0; j < labels.length; j++) {
            let label = foldText(labels[j]);
            if (folded.indexOf(label) === 0 || folded.indexOf(label + ":") === 0 || folded.indexOf(label + "：") === 0) {
                return cleanText(text.replace(/^[^:：]+[:：]\s*/, ""));
            }
        }
    }
    return "";
}

function genresFromDoc(doc) {
    let data = [];
    let seen = {};
    let links = doc.select("a[href*='/fenlei/']");
    for (let i = 0; i < links.size(); i++) {
        let a = links.get(i);
        let href = normalizeUrl(a.attr("href"));
        let title = cleanText(a.text());
        if (!href || !title || title.length > 12 || seen[href]) continue;
        if (href.indexOf("/fenlei/") < 0) continue;
        seen[href] = true;
        data.push({
            title: title,
            input: href,
            script: "gen.js"
        });
    }
    return data;
}

function chineseNumber(text) {
    text = cleanText(text);
    let arabic = /(\d+)/.exec(text);
    if (arabic) return parseInt(arabic[1], 10);
    let map = {"零": 0, "〇": 0, "一": 1, "二": 2, "两": 2, "兩": 2, "三": 3, "四": 4, "五": 5, "六": 6, "七": 7, "八": 8, "九": 9};
    let total = 0;
    let section = 0;
    let number = 0;
    for (let i = 0; i < text.length; i++) {
        let ch = text.charAt(i);
        if (map[ch] !== undefined) {
            number = map[ch];
        } else if (ch === "十") {
            section += (number || 1) * 10;
            number = 0;
        } else if (ch === "百") {
            section += (number || 1) * 100;
            number = 0;
        } else if (ch === "千") {
            section += (number || 1) * 1000;
            number = 0;
        } else if (ch === "萬" || ch === "万") {
            total += (section + number) * 10000;
            section = 0;
            number = 0;
        }
    }
    return total + section + number;
}

function chapterNumber(text) {
    let value = cleanText(text || "");
    let match = /第\s*([0-9零〇一二两兩三四五六七八九十百千万萬]+)\s*[章节節回卷]/i.exec(value);
    if (match) return chineseNumber(match[1]);
    match = /(\d+)/.exec(value);
    return match ? parseInt(match[1], 10) : 0;
}

function textToHtml(text) {
    text = (text || "").replace(/\r/g, "");
    let lines = text.split("\n");
    let parts = [];
    for (let i = 0; i < lines.length; i++) {
        let raw = lines[i].replace(/\u0000/g, "").replace(/\s+$/g, "");
        let plain = raw.replace(/^[\s\u3000]+/g, "");
        if (!plain) continue;
        parts.push("<p>" + escapeHtml(cleanText(plain)) + "</p>");
    }
    return parts.join("\n");
}

function rawHtmlToText(html) {
    html = html || "";
    let marker = "__VBOOK_BR__";
    html = html.replace(/<script[\s\S]*?<\/script>/gi, "");
    html = html.replace(/<style[\s\S]*?<\/style>/gi, "");
    html = html.replace(/<br\s*\/?>/gi, marker);
    html = html.replace(/<\/p>/gi, marker);
    html = html.replace(/<\/div>/gi, marker);
    let text = Html.parse("<div>" + html + "</div>").text();
    text = text.replace(new RegExp(marker + "\\s*" + marker, "g"), marker);
    text = text.replace(new RegExp("\\s*" + marker + "\\s*", "g"), "\n");
    return text;
}
