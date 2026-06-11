var BASE_URL = "https://itruyenchu.org";
var API_URL = "https://api.ngoctieucac.link";
var ASSET_URL = "https://assets.itruyenchu.org";
var DATA_URL = "https://assets.ngoctieucac.link";
var AUTH_COOKIE = "";
var AUTH_TOKEN = "";

function extractAuthToken(value) {
    if (!value) return "";
    value = ("" + value).trim();
    if (value.indexOf("Bearer ") === 0) value = value.substring(7).trim();
    if (value.split(".").length === 3 && value.indexOf("{") === -1) return value;

    try {
        let decoded = decodeURIComponent(value);
        if (decoded !== value) return extractAuthToken(decoded);
    } catch (error) {
    }

    try {
        let json = JSON.parse(value);
        return findAuthToken(json);
    } catch (error) {
    }

    let match = value.match(/eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/);
    return match ? match[0] : "";
}

function findAuthToken(value) {
    if (!value) return "";
    if (typeof value === "string") return extractAuthToken(value);
    if (typeof value !== "object") return "";

    let preferred = ["accessToken", "token", "jwt", "access_token"];
    for (let i = 0; i < preferred.length; i++) {
        if (value[preferred[i]]) {
            let token = extractAuthToken(value[preferred[i]]);
            if (token) return token;
        }
    }

    for (let key in value) {
        let token = findAuthToken(value[key]);
        if (token) return token;
    }
    return "";
}

try {
    if (CONFIG_URL && CONFIG_URL.indexOf("accessToken=") !== -1) {
        AUTH_COOKIE = CONFIG_URL;
    } else {
        AUTH_TOKEN = extractAuthToken(CONFIG_URL);
    }
} catch (error) {
}

try {
    if (!AUTH_TOKEN && typeof localStorage !== "undefined") {
        let authStorage = localStorage.getItem("auth-storage");
        if (authStorage) AUTH_TOKEN = extractAuthToken(authStorage);
    }
} catch (error) {
}

try {
    if (!AUTH_TOKEN && typeof window !== "undefined" && window.localStorage) {
        let authStorage = window.localStorage.getItem("auth-storage");
        if (authStorage) AUTH_TOKEN = extractAuthToken(authStorage);
    }
} catch (error) {
}

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return BASE_URL + url;
    if (!url.startsWith("http")) return BASE_URL + "/" + url;
    return url.replace(/^https?:\/\/(www\.)?itruyenchu\.org/, BASE_URL);
}

function cleanText(text) {
    return text ? text.replace(/\s+/g, " ").trim() : "";
}

function stripHtml(html) {
    return html ? cleanText(html.replace(/<[^>]+>/g, " ")) : "";
}

function getSlug(url) {
    url = normalizeUrl(url).split("?")[0].replace(/\/$/, "");
    let match = url.match(/\/truyen\/([^\/]+)/);
    if (match) return match[1];
    let parts = url.split("/");
    return parts[parts.length - 1] || "";
}

function getChapterNumber(url) {
    let match = (url || "").match(/\/chuong-(\d+)/);
    return match ? parseInt(match[1]) : 1;
}

function bookUrl(slug) {
    return BASE_URL + "/truyen/" + slug;
}

function chapterUrl(slug, number) {
    return BASE_URL + "/truyen/" + slug + "/chuong-" + number;
}

function coverUrl(slug) {
    return ASSET_URL + "/book-cover/" + slug + "/banner-small.webp";
}

function fullCoverUrl(slug) {
    return ASSET_URL + "/book-cover/" + slug + "/banner.webp";
}

function fetchDocument(url) {
    let response = fetch(normalizeUrl(url));
    if (!response.ok) return null;
    return response.html();
}

function authFetch(url) {
    if (AUTH_TOKEN) {
        return fetch(normalizeUrl(url), {
            headers: {
                "Authorization": "Bearer " + AUTH_TOKEN
            }
        });
    }
    if (AUTH_COOKIE) {
        return fetch(normalizeUrl(url), {
            headers: {
                "Cookie": AUTH_COOKIE
            }
        });
    }
    return fetch(normalizeUrl(url));
}

function fetchJson(url) {
    let response = fetch(url);
    if (!response.ok) return null;
    return response.json();
}

function apiBooksUrl(params) {
    let query = [];
    for (let key in params) {
        if (params[key] !== null && params[key] !== undefined && params[key] !== "") {
            query.push(key + "=" + encodeURIComponent(params[key]));
        }
    }
    return API_URL + "/books" + (query.length ? "?" + query.join("&") : "");
}

function absoluteAsset(url) {
    if (!url) return "";
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return BASE_URL + url;
    return url;
}

function decodeEntities(text) {
    if (!text) return "";
    return text.replace(/&quot;/g, '"')
        .replace(/&#39;/g, "'")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .trim();
}

function uniqueBooksFromAnchors(doc) {
    let data = [];
    let seen = {};
    doc.select("a[href^='/truyen/']").forEach(function (a) {
        let href = a.attr("href");
        if (!href || href.indexOf("/chuong-") !== -1) return;
        let slug = getSlug(href);
        if (!slug || seen[slug]) return;

        let name = a.attr("title") || a.text();
        name = cleanText(name.replace(/^Đọc truyện\s*/i, ""));
        let img = a.select("img").first();
        if (!name && img) name = img.attr("alt");
        name = cleanText((name || "").replace(/^Bìa truyện\s*/i, ""));
        if (!name) return;

        let cover = img ? absoluteAsset(img.attr("src")) : coverUrl(slug);
        let desc = "";
        let chapter = a.select("div:contains(Ch.)").last();
        if (chapter) desc = cleanText(chapter.text());

        seen[slug] = true;
        data.push({
            name: name,
            link: bookUrl(slug),
            cover: cover,
            description: desc,
            host: BASE_URL
        });
    });
    return data;
}

function bookDescription(book) {
    let parts = [];
    if (book.tacGia) parts.push("Tác giả: " + book.tacGia);
    if (book.currentChapter) parts.push("Số chương: " + book.currentChapter);
    if (book.isFree) parts.push("Miễn phí");
    return parts.join(" - ");
}

function mapApiBooks(json) {
    let books = json && json.data ? json.data : [];
    let data = [];
    books.forEach(function (book) {
        if (!book || !book.slug || !book.title) return;
        data.push({
            name: book.title,
            link: bookUrl(book.slug),
            cover: book.bannerURL || coverUrl(book.slug),
            description: bookDescription(book),
            host: BASE_URL
        });
    });
    return data;
}

function nextPage(json, page) {
    let current = page ? parseInt(page) : 1;
    if (json && json.totalPages && current < parseInt(json.totalPages)) return "" + (current + 1);
    return null;
}

function parseBookSchema(doc) {
    let scripts = doc.select("script[type='application/ld+json']");
    for (let i = 0; i < scripts.size(); i++) {
        try {
            let json = JSON.parse(scripts.get(i).html());
            if (json && json["@type"] === "Book") return json;
        } catch (e) {
        }
    }
    return null;
}

function parseTotalChapters(doc, schema) {
    if (schema && schema.numberOfChapters) return parseInt(schema.numberOfChapters);
    let html = doc.html();
    let match = html.match(/(\d+)<!--\s*-->\s*chương/i) || html.match(/numberOfChapters"\s*:\s*(\d+)/);
    return match ? parseInt(match[1]) : 0;
}

function responseContent(text) {
    if (!text || !cleanText(text)) return null;
    let lines = text.split(/\r?\n/);
    let html = [];
    lines.forEach(function (line) {
        line = cleanText(line);
        if (line) html.push("<p>" + line + "</p>");
    });
    return html.join("");
}
