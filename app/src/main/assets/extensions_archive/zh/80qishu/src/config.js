let BASE_URL = "https://www.80qishu.com";
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
    return url.replace(/^http:\/\/www\.80qishu\.com/i, stripTrailingSlash(BASE_URL));
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
    headers["Accept"] = headers["Accept"] || "text/html,text/plain,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    headers["Accept-Language"] = headers["Accept-Language"] || "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7";
    headers["Referer"] = headers["Referer"] || BASE_URL + "/";
    headers["User-Agent"] = headers["User-Agent"] || MOBILE_UA;
    options.headers = headers;
    url = normalizeUrl(url);
    if (/[^\x00-\x7F]/.test(url)) url = encodeURI(url);
    return fetch(url, options);
}

function getDoc(url) {
    LAST_ERROR = "";
    try {
        let response = request(url);
        if (response && response.ok) return response.html();
        LAST_ERROR = "HTTP " + (response ? response.status : "");
    } catch (error) {
        LAST_ERROR = "无法加载 80qishu 页面：" + error;
    }
    return null;
}

function getText(url, referer) {
    LAST_ERROR = "";
    try {
        let response = request(url, {
            headers: {
                "Accept": "text/plain,*/*;q=0.8",
                "Referer": referer || BASE_URL + "/"
            }
        });
        if (response && response.ok) return response.text() || "";
        LAST_ERROR = "HTTP " + (response ? response.status : "");
    } catch (error) {
        LAST_ERROR = "无法下载 80qishu TXT：" + error;
    }
    return "";
}

function postSearchDoc(key) {
    LAST_ERROR = "";
    try {
        let body = "show=title%2Csoftsay%2Csoftwriter&keyboard=" + encodeURIComponent(key || "") + "&tbname=download&tempid=1";
        let response = request(BASE_URL + "/e/search/index.php", {
            method: "POST",
            headers: {
                "Accept": "text/html,*/*;q=0.8",
                "Content-Type": "application/x-www-form-urlencoded",
                "Origin": BASE_URL,
                "Referer": BASE_URL + "/"
            },
            body: body
        });
        if (response && response.ok) return response.html();
        LAST_ERROR = "HTTP " + (response ? response.status : "");
    } catch (error) {
        LAST_ERROR = "无法搜索 80qishu：" + error;
    }
    return null;
}

function loadError() {
    return Response.error(LAST_ERROR || "无法加载 80qishu。");
}

function pathOf(url) {
    url = normalizeUrl(url);
    let match = /^https?:\/\/[^/]+(\/[^?#]*)?/i.exec(url);
    return match && match[1] ? match[1] : "/";
}

function isStoryUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/[a-z]+\/(?:\d{4}\/\d{2}\/)?txt\d+\.html$/i.test(path);
}

function isDownUrl(url) {
    let path = pathOf(url).replace(/\/+$/, "");
    return /^\/down\/txt[^/]+qishu\.html$/i.test(path);
}

function isTxtUrl(url) {
    return /^https?:\/\/[dt]\.80qishu\.com\/d\/file\/down\/.*\.txt(?:[?#].*)?$/i.test(url || "");
}

function storyUrlFromAny(url) {
    url = normalizeUrl(url);
    if (isStoryUrl(url)) return url;
    let match = /^(https?:\/\/[^/]+\/[a-z]+\/(?:\d{4}\/\d{2}\/)?txt\d+\.html)/i.exec(url);
    return match ? normalizeUrl(match[1]) : url;
}

function imageAttr(e) {
    if (!e) return "";
    let srcset = e.attr("data-srcset") || e.attr("srcset");
    if (srcset) return cleanText(srcset.split(",")[0].split(" ")[0]);
    return e.attr("data-original") || e.attr("data-src") || e.attr("data-lazy-src") || e.attr("src");
}

function imageFromNode(e) {
    return normalizeImage(imageAttr(e));
}

function metaContent(doc, selector) {
    let node = doc.select(selector).first();
    return node ? cleanText(node.attr("content")) : "";
}

function metaImage(doc) {
    return normalizeImage(metaContent(doc, "meta[property=og:image], meta[name=twitter:image]"));
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

function stripBookMarks(text) {
    return cleanText(text).replace(/^[\u300a\u300c\u300e"']+/, "").replace(/[\u300b\u300d\u300f"']+$/, "");
}

function textByPrefix(node, prefix) {
    let items = node.select("p, li, div");
    for (let i = 0; i < items.size(); i++) {
        let text = cleanText(items.get(i).text());
        if (text.indexOf(prefix) === 0) return cleanText(text.substring(prefix.length).replace(/^[:\uff1a]\s*/, ""));
    }
    return "";
}

function genresFromDoc(doc) {
    let data = [];
    let seen = {};
    doc.select(".bookcover p a[href]").forEach(function(a) {
        let href = normalizeUrl(a.attr("href"));
        let path = pathOf(href);
        let title = cleanText(a.text());
        if (!title || !/^\/(?:nansheng|yanqing|tongren|dushi|xuanhuan|xiuzhen|wangyou|lishi|kehuan|qita)\/$/i.test(path) || seen[href]) return;
        seen[href] = true;
        data.push({title: title, input: href, script: "gen.js"});
    });
    return data;
}

function pageNumberFromUrl(url) {
    let path = pathOf(url);
    let match = /\/index_(\d+)\.html$/i.exec(path);
    if (match) return parseInt(match[1]);
    return 1;
}

function listPageUrl(url, page) {
    if (!url) url = "/new/";
    if (!page || page === "1") return normalizeUrl(url);
    if (("" + page).indexOf("http") === 0 || ("" + page).indexOf("/") === 0) return normalizeUrl(page);
    let target = normalizeUrl(url);
    let number = encodeURIComponent(page);
    if (/\/index(?:_\d+)?\.html$/i.test(target)) return target.replace(/\/index(?:_\d+)?\.html$/i, "/index_" + number + ".html");
    return stripTrailingSlash(target) + "/index_" + number + ".html";
}

function nextPage(doc, currentUrl) {
    let current = pageNumberFromUrl(currentUrl || "");
    let best = 0;
    let bestHref = "";
    let links = doc.select(".pages a[href], a[href*='index_']");
    for (let i = 0; i < links.size(); i++) {
        let href = links.get(i).attr("href");
        let page = pageNumberFromUrl(href);
        if (page > current && (!best || page < best)) {
            best = page;
            bestHref = href;
        }
    }
    return bestHref ? normalizeUrl(bestHref) : "";
}

function parseListItems(doc) {
    let data = [];
    let seen = {};
    let nodes = doc.select(".imgtextlist li");
    if (nodes.size() === 0) nodes = doc.select("li");
    nodes.forEach(function(e) {
        let linkNode = null;
        let links = e.select("p.title a[href], a.orange[href], a[href*='txt']");
        for (let i = 0; i < links.size(); i++) {
            let item = links.get(i);
            if (!isStoryUrl(item.attr("href"))) continue;
            if (cleanText(item.text() || item.attr("title"))) {
                linkNode = item;
                break;
            }
            if (!linkNode) linkNode = item;
        }
        if (!linkNode || !isStoryUrl(linkNode.attr("href"))) return;
        let link = normalizeUrl(linkNode.attr("href"));
        if (seen[link]) return;
        let name = stripBookMarks(cleanText(linkNode.text() || linkNode.attr("title")));
        if (!name) {
            let titleNode = e.select("p.title a[href], a.orange[href]").first();
            name = titleNode ? stripBookMarks(cleanText(titleNode.text() || titleNode.attr("title"))) : "";
        }
        if (!name) return;
        let cover = imageFromNode(e.select("a.pic img, img").first());
        let author = textByPrefix(e, "\u4f5c\u8005");
        let update = textByPrefix(e, "\u66f4\u65b0");
        let size = textByPrefix(e, "\u5927\u5c0f");
        let intro = textByPrefix(e, "\u7b80\u4ecb");
        let desc = [];
        if (author) desc.push("作者：" + author);
        if (update) desc.push("更新：" + update);
        if (size) desc.push("大小：" + size);
        if (intro) desc.push(intro);
        seen[link] = true;
        data.push({name: name, link: link, cover: cover, description: desc.join(" - "), host: BASE_URL});
    });
    return data;
}

function findDownloadPage(doc, storyUrl) {
    let link = doc.select("a[href*='/down/'][href*='txt']").first();
    if (link) return normalizeUrl(link.attr("href"));
    let match = /txt(\d+)\.html/i.exec(storyUrl || "");
    return match ? BASE_URL + "/down/txt7c" + match[1] + "qishu.html" : "";
}

function findTxtUrls(doc) {
    let data = [];
    let seen = {};
    doc.select("a[href$='.txt'], a[href*='.txt']").forEach(function(a) {
        let href = normalizeUrl(a.attr("href"));
        if (!isTxtUrl(href) || seen[href]) return;
        seen[href] = true;
        data.push(href);
    });
    return data;
}

function resolveTxtUrls(storyUrl) {
    let doc = getDoc(storyUrl);
    if (!doc) return [];
    let downUrl = findDownloadPage(doc, storyUrl);
    if (!downUrl) return [];
    let downDoc = getDoc(downUrl);
    if (!downDoc) return [];
    return findTxtUrls(downDoc);
}

function queryParam(url, name) {
    let match = new RegExp("[?&]" + name + "=([^&#]*)").exec(url || "");
    if (!match) return "";
    try {
        return decodeURIComponent(match[1]);
    } catch (error) {
        return match[1];
    }
}

function readerUrl(txtUrl, storyUrl, index) {
    return BASE_URL + "/read/80qishu?txt=" + encodeURIComponent(txtUrl) + "&story=" + encodeURIComponent(storyUrl || "") + "&i=" + index;
}

function normalizeNovelText(text) {
    return (text || "").replace(/^\uFEFF/, "").replace(/\r\n/g, "\n").replace(/\r/g, "\n");
}

function chineseNumber(text) {
    text = cleanText(text);
    let arabic = /(\d+)/.exec(text);
    if (arabic) return parseInt(arabic[1]);
    let map = {"\u96f6": 0, "\u3007": 0, "\u4e00": 1, "\u4e8c": 2, "\u4e24": 2, "\u4e09": 3, "\u56db": 4, "\u4e94": 5, "\u516d": 6, "\u4e03": 7, "\u516b": 8, "\u4e5d": 9};
    let total = 0;
    let section = 0;
    let number = 0;
    for (let i = 0; i < text.length; i++) {
        let ch = text.charAt(i);
        if (map[ch] !== undefined) {
            number = map[ch];
        } else if (ch === "\u5341") {
            section += (number || 1) * 10;
            number = 0;
        } else if (ch === "\u767e") {
            section += (number || 1) * 100;
            number = 0;
        } else if (ch === "\u5343") {
            section += (number || 1) * 1000;
            number = 0;
        } else if (ch === "\u4e07") {
            total += (section + number) * 10000;
            section = 0;
            number = 0;
        }
    }
    return total + section + number;
}

function isChapterNumberChar(ch) {
    return /[0-9\u96f6\u3007\u4e00\u4e8c\u4e24\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07]/.test(ch || "");
}

function parseDirectChapterLine(line) {
    line = cleanText(line);
    if (!line || line.charAt(0) !== "\u7b2c") return null;

    let i = 1;
    while (i < line.length && /\s/.test(line.charAt(i))) i++;
    let start = i;
    while (i < line.length && isChapterNumberChar(line.charAt(i))) i++;
    if (i === start) return null;

    while (i < line.length && /\s/.test(line.charAt(i))) i++;
    if (i >= line.length || "\u7ae0\u8282\u56de\u5377\u90e8\u96c6".indexOf(line.charAt(i)) < 0) return null;

    let raw = chineseNumber(line.substring(start, i));
    if (!raw) return null;
    return {
        raw: raw,
        suffix: cleanText(line.substring(i + 1)),
        source: line,
        kind: "direct"
    };
}

function parsePipeChapterLine(line) {
    line = cleanText(line);
    let match = /^(\d{1,3})\|(\d{2})$/.exec(line);
    if (!match) return null;
    return {
        raw: parseInt(match[1]),
        suffix: match[2],
        source: line,
        kind: "pipe"
    };
}

function chapterDisplayTitle(order, suffix) {
    let title = "\u7b2c" + order + "\u7ae0";
    suffix = cleanText(suffix || "").replace(/^[\s:：|]+/, "");
    if (!suffix || /^[0-9~\uff5e|._=-]+$/.test(suffix)) return title;
    return title + (/^[\(\[\u3010\uff08]/.test(suffix) ? "" : " ") + suffix;
}

function chapterCandidates(text) {
    let lines = text.split("\n");
    let offsets = [];
    let cursor = 0;
    for (let i = 0; i < lines.length; i++) {
        offsets.push(cursor);
        cursor += lines[i].length + 1;
    }

    let data = [];
    for (let j = 0; j < lines.length; j++) {
        let line = cleanText(lines[j]);
        if (!line) continue;

        let direct = parseDirectChapterLine(line);
        if (direct) {
            let item = {
                index: offsets[j],
                bodyStart: j + 1 < offsets.length ? offsets[j + 1] : text.length,
                lineIndex: j,
                raw: direct.raw,
                suffix: direct.suffix,
                source: direct.source,
                kind: direct.kind
            };
            if (data.length > 0) {
                let prev = data[data.length - 1];
                if (prev.kind === "pipe" && prev.raw === item.raw && item.lineIndex - prev.lineIndex <= 3) {
                    data[data.length - 1] = item;
                    continue;
                }
            }
            data.push(item);
            continue;
        }

        let pipe = parsePipeChapterLine(line);
        if (pipe) {
            data.push({
                index: offsets[j],
                bodyStart: j + 1 < offsets.length ? offsets[j + 1] : text.length,
                lineIndex: j,
                raw: pipe.raw,
                suffix: pipe.suffix,
                source: pipe.source,
                kind: pipe.kind
            });
        }
    }
    return data;
}

function resolveChapterCandidates(candidates) {
    let resolved = [];
    let previous = 0;
    for (let i = 0; i < candidates.length;) {
        let current = candidates[i];
        if (current.raw === previous + 1) {
            current.order = current.raw;
            resolved.push(current);
            previous = current.order;
            i++;
            continue;
        }

        let anchor = -1;
        for (let j = i + 1; j < candidates.length; j++) {
            let next = candidates[j];
            if (next.raw <= previous) continue;
            let gap = next.raw - previous - 1;
            let count = j - i;
            if (gap === count) {
                anchor = j;
                break;
            }
        }

        if (anchor >= 0) {
            for (let k = i; k < anchor; k++) {
                candidates[k].order = previous + (k - i) + 1;
                resolved.push(candidates[k]);
            }
            previous = resolved[resolved.length - 1].order;
            i = anchor;
            continue;
        }

        current.order = current.raw > previous ? current.raw : previous + 1;
        resolved.push(current);
        previous = current.order;
        i++;
    }
    return resolved;
}

function splitChapters(text) {
    text = normalizeNovelText(text);
    let re = chapterTitleRegex();
    let matches = [];
    let m;
    while ((m = re.exec(text)) !== null) {
        let title = cleanText(m[0]);
        if (!title) continue;
        matches.push({index: m.index, bodyStart: lineEnd(text, m.index), title: title, order: chapterOrdinal(title)});
    }
    if (matches.length === 0) {
        return [{title: "全文", order: 1, body: text}];
    }

    let start = 0;
    if (matches[0].order > 1) {
        for (let i = 1; i < matches.length; i++) {
            if (matches[i].order === 1) {
                start = i;
                break;
            }
        }
    }

    let chapters = [];
    for (let j = start; j < matches.length; j++) {
        let next = j + 1 < matches.length ? matches[j + 1].index : text.length;
        let body = text.substring(matches[j].bodyStart, next);
        chapters.push({title: matches[j].title, order: matches[j].order || (chapters.length + 1), body: body});
    }
    return chapters;
}

function tocName(chapter, index) {
    let title = cleanText(chapter.title);
    if (title) return title;
    return "第" + (index + 1) + "章";
}

function htmlEscape(text) {
    return (text || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function isJunkLine(line) {
    line = cleanText(line);
    if (!line) return true;
    return line.indexOf("QiShu") >= 0 ||
        line.indexOf("80qishu") >= 0 ||
        line.indexOf("www.") >= 0 ||
        line.indexOf("\u58f0\u660e\uff1a") === 0 ||
        line.indexOf("\u7528\u6237\u4e0a\u4f20\u4e4b\u5185\u5bb9") >= 0 ||
        line.indexOf("\u672c\u4e66\u6765\u81ea") >= 0 ||
        line.indexOf("\u66f4\u591a\u7cbe\u6821") >= 0 ||
        /^[-_=]{6,}$/.test(line);
}

function textToHtml(text) {
    text = normalizeNovelText(text);
    let lines = text.split("\n");
    let html = [];
    for (let i = 0; i < lines.length; i++) {
        let line = cleanText(lines[i]);
        if (isJunkLine(line)) continue;
        html.push("<p>" + htmlEscape(line) + "</p>");
    }
    return html.join("\n");
}

function splitChapters(text) {
    text = normalizeNovelText(text);
    let matches = resolveChapterCandidates(chapterCandidates(text));
    if (matches.length === 0) return [{title: "\u5168\u6587", order: 1, body: text}];

    let chapters = [];
    for (let j = 0; j < matches.length; j++) {
        let next = j + 1 < matches.length ? matches[j + 1].index : text.length;
        let body = text.substring(matches[j].bodyStart, next);
        chapters.push({
            title: chapterDisplayTitle(matches[j].order, matches[j].suffix),
            order: matches[j].order || (chapters.length + 1),
            body: body
        });
    }
    return chapters;
}

function tocName(chapter, index) {
    let title = cleanText(chapter.title);
    if (title) return title;
    return "\u7b2c" + (index + 1) + "\u7ae0";
}

function isJunkLine(line) {
    line = cleanText(line);
    if (!line) return true;
    return line.indexOf("QiShu") >= 0 ||
        line.indexOf("80qishu") >= 0 ||
        line.indexOf("www.") >= 0 ||
        /^\d{1,3}\|\d{2}$/.test(line) ||
        /^123\u8a00\u60c5/.test(line) ||
        /^\u5185\u5bb9\u63d0\u8981[:\uff1a]/.test(line) ||
        /^\u8c22\u7edd\u8f6c\u8f7d/.test(line) ||
        /^[~\uff5e]{1,}$/.test(line) ||
        line.indexOf("\u58f0\u660e\uff1a") === 0 ||
        line.indexOf("\u7528\u6237\u4e0a\u4f20\u4e4b\u5185\u5bb9") >= 0 ||
        line.indexOf("\u672c\u4e66\u6765\u81ea") >= 0 ||
        line.indexOf("\u66f4\u591a\u7cbe\u6821") >= 0 ||
        /^[-_=]{6,}$/.test(line);
}

function execute() {
    return Response.success({baseUrl: BASE_URL, ok: true});
}
