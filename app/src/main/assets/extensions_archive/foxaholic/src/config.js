let BASE_URL = 'https://www.foxaholic.com';
try {
    if (CONFIG_URL) BASE_URL = stripTrailingSlash(CONFIG_URL);
} catch (error) {
}

let LAST_ERROR = "";
let MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36";

function cleanText(text) {
    if (!text) return "";
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
    if (!url) return url;
    return ("" + url).replace(/\/+$/, "");
}

function normalizeUrl(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return stripTrailingSlash(BASE_URL) + url;
    if (!/^https?:\/\//i.test(url)) return stripTrailingSlash(BASE_URL) + "/" + url.replace(/^\/+/, "");
    return url.replace(/^https?:\/\/(?:www\.)?foxaholic\.com/i, stripTrailingSlash(BASE_URL));
}

function normalizeImage(url) {
    if (!url) return "";
    url = ("" + url).replace(/&amp;/g, "&").trim();
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return stripTrailingSlash(BASE_URL) + url;
    if (!/^https?:\/\//i.test(url)) return stripTrailingSlash(BASE_URL) + "/" + url.replace(/^\/+/, "");
    return url;
}

function request(url, options) {
    options = options || {};
    let headers = options.headers || {};
    headers["Accept"] = headers["Accept"] || "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    headers["Accept-Language"] = headers["Accept-Language"] || "en-US,en;q=0.9";
    headers["Referer"] = headers["Referer"] || BASE_URL + "/";
    headers["User-Agent"] = headers["User-Agent"] || MOBILE_UA;
    options.headers = headers;
    return fetch(normalizeUrl(url), options);
}

function isBlockedText(text) {
    text = foldText(text || "");
    return text.indexOf("just a moment") >= 0 ||
        text.indexOf("checking your browser") >= 0 ||
        text.indexOf("challenge-platform") >= 0 ||
        text.indexOf("/cdn-cgi/challenge-platform/") >= 0 ||
        (text.indexOf("cloudflare") >= 0 && text.indexOf("challenge") >= 0 && text.indexOf("cf-chl") >= 0);
}

function getText(url, options) {
    LAST_ERROR = "";
    try {
        let response = request(url, options);
        if (!response) {
            LAST_ERROR = "Khong nhan duoc phan hoi tu Foxaholic.";
            return "";
        }
        if (!response.ok) {
            LAST_ERROR = "Foxaholic bi chan HTTP/Cloudflare. Site co the can mo WebView de lay cookie.";
            return "";
        }
        let text = response.text() || "";
        if (isBlockedText(text)) {
            LAST_ERROR = "Foxaholic bi Cloudflare challenge. Mo link bang trinh duyet/WebView roi thu lai.";
            return "";
        }
        return text;
    } catch (error) {
        LAST_ERROR = "Khong tai duoc Foxaholic: " + error;
    }
    return "";
}

function getDoc(url) {
    LAST_ERROR = "";
    try {
        let response = request(url);
        if (response && response.ok) {
            let doc = response.html();
            if (!isChallengeDoc(doc)) return doc;
        }
    } catch (error) {
    }
    return browserDoc(url);
}

function postText(url, body, referer) {
    return getText(url, {
        method: "POST",
        headers: {
            "Accept": "text/html, */*; q=0.01",
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Origin": BASE_URL,
            "Referer": referer || BASE_URL + "/",
            "X-Requested-With": "XMLHttpRequest"
        },
        body: body || ""
    });
}

function loadError() {
    return Response.error(LAST_ERROR || "Khong tai duoc Foxaholic.");
}

function isChallengeDoc(doc) {
    if (!doc) return true;
    let html = "";
    try {
        html = doc.html ? doc.html() : "";
    } catch (error) {
    }
    let title = "";
    try {
        title = cleanText(doc.select("title").text());
    } catch (error2) {
    }
    return title.indexOf("Just a moment") >= 0 ||
        html.indexOf("cf-chl") >= 0 ||
        html.indexOf("__cf_chl_") >= 0 ||
        html.indexOf("window._cf_chl_opt") >= 0 ||
        html.indexOf("challenge-platform") >= 0 ||
        html.indexOf("Enable JavaScript and cookies") >= 0;
}

function browserDoc(url) {
    let browser = null;
    try {
        browser = Engine.newBrowser();
        let ua = MOBILE_UA;
        try {
            ua = UserAgent.android();
        } catch (uaError) {
        }
        browser.setUserAgent(ua);
        let doc = browser.launch(normalizeUrl(url), 45000);
        for (let i = 0; i < 8 && isChallengeDoc(doc); i++) {
            sleep(3000);
            doc = browser.html();
        }
        if (isChallengeDoc(doc)) {
            LAST_ERROR = "Foxaholic van bi Cloudflare chan trong browser an. Site co the yeu cau captcha/Turnstile thu cong.";
            return null;
        }
        return doc;
    } catch (error) {
        LAST_ERROR = "Khong mo duoc browser an de vuot Cloudflare: " + error;
        return null;
    } finally {
        try {
            if (browser) browser.close();
        } catch (closeError) {
        }
    }
}

function pathOf(url) {
    url = normalizeUrl(url);
    let match = /^https?:\/\/[^/]+(\/[^?#]*)?/i.exec(url);
    return match && match[1] ? match[1] : "/";
}

function isStoryUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/novel\/[^/]+$/i.test(path);
}

function isChapterUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/novel\/[^/]+\/(?!ajax\b)[^/]+$/i.test(path);
}

function storyUrlFromAny(url) {
    url = normalizeUrl(url);
    let match = /^(https?:\/\/[^/]+\/novel\/[^/?#\/]+)/i.exec(url);
    return match ? match[1] + "/" : url;
}

function imageAttr(e) {
    if (!e) return "";
    let srcset = e.attr("data-srcset") || e.attr("srcset");
    if (srcset) return cleanText(srcset.split(",")[0].split(" ")[0]);
    return e.attr("data-original") || e.attr("data-src") || e.attr("data-lazy-src") || e.attr("data-cfsrc") || e.attr("src");
}

function imageFromNode(e) {
    return normalizeImage(imageAttr(e));
}

function metaContent(doc, selector) {
    let node = doc.select(selector).first();
    return node ? cleanText(node.attr("content")) : "";
}

function metaImage(doc) {
    return normalizeImage(metaContent(doc, "meta[property=og:image], meta[property='og:image:secure_url'], meta[name=twitter:image], meta[itemprop=image]"));
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

function stripHtml(text) {
    return cleanText((text || "").replace(/<[^>]+>/g, " "));
}

function chapterNumber(text) {
    let folded = foldText(text || "").replace(/_/g, "-");
    let vm = /v(\d+)\s*c(\d+(?:[-.]\d+)?)/i.exec(folded);
    if (vm) return parseFloat(vm[1]) * 10000 + parseFloat(vm[2].replace("-", "."));
    let match = /(?:chapter|chap|chuong|episode|ep|part|c)\s*[-:#.]?\s*(\d+(?:[-.]\d+)?)/i.exec(folded);
    if (!match) match = /(\d+(?:[-.]\d+)?)/.exec(folded);
    return match ? parseFloat(match[1].replace("-", ".")) : 0;
}

function sortChapters(data) {
    if (!data || data.length < 2) return data;
    let mapped = [];
    let count = 0;
    for (let i = 0; i < data.length; i++) {
        let value = chapterNumber(data[i].name + " " + data[i].url);
        if (value > 0) count++;
        mapped.push({item: data[i], index: i, value: value});
    }
    if (count < 2) return data;
    mapped.sort(function(a, b) {
        if (a.value > 0 && b.value > 0 && a.value !== b.value) return a.value - b.value;
        if (a.value === 0 && b.value > 0) return -1;
        if (a.value > 0 && b.value === 0) return 1;
        return a.index - b.index;
    });
    let sorted = [];
    for (let j = 0; j < mapped.length; j++) sorted.push(mapped[j].item);
    return sorted;
}

function pageNumberFromUrl(url) {
    let m = /\/page\/(\d+)/i.exec(url || "");
    if (!m) m = /[?&](?:paged|page)=(\d+)/i.exec(url || "");
    return m ? parseInt(m[1]) : 1;
}

function listPageUrl(url, page) {
    if (!url) url = "/novel/";
    if (!page || page === "1") return normalizeUrl(url);
    if (("" + page).indexOf("http") === 0 || ("" + page).indexOf("/") === 0) return normalizeUrl(page);

    let target = normalizeUrl(url);
    let parts = target.split("?");
    let base = stripTrailingSlash(parts[0].replace(/\/page\/\d+\/?$/i, ""));
    let query = parts.length > 1 ? "?" + parts.slice(1).join("?") : "";
    return base + "/page/" + page + "/" + query;
}

function nextPage(doc, currentUrl) {
    let selectors = [
        "a.nextpostslink[href]",
        ".wp-pagenavi a.nextpostslink[href]",
        ".nav-links a.next[href]",
        ".pagination a.next[href]",
        "a.page-numbers.next[href]",
        "a[rel=next][href]"
    ];
    for (let i = 0; i < selectors.length; i++) {
        let e = doc.select(selectors[i]).first();
        if (e) return normalizeUrl(e.attr("href"));
    }

    let current = pageNumberFromUrl(currentUrl || "");
    let links = doc.select("a[href*='/page/'], a[href*='paged=']");
    let best = 0;
    let bestHref = "";
    for (let j = 0; j < links.size(); j++) {
        let href = links.get(j).attr("href");
        let page = pageNumberFromUrl(href);
        if (page > current && (!best || page < best)) {
            best = page;
            bestHref = href;
        }
    }
    return bestHref ? normalizeUrl(bestHref) : "";
}

function execute() {
    return Response.success({baseUrl: BASE_URL, ok: true});
}
