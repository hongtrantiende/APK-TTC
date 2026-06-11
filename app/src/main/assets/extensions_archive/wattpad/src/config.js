let BASE_URL = "https://wattpad.com.vn";

try {
    if (CONFIG_URL) {
        BASE_URL = CONFIG_URL.replace(/\/+$/, "");
    }
} catch (error) {
}

function absoluteUrl(url) {
    if (!url) return "";
    if (/^https?:\/\//i.test(url)) return url;
    if (url.indexOf("//") === 0) return "https:" + url;
    if (url.charAt(0) === "/") return BASE_URL + url;
    return BASE_URL + "/" + url.replace(/^\/+/, "");
}

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    if (/^https?:\/\//i.test(url)) {
        return url.replace(/^(https?:\/\/)(www\.)?[^\/?#]+/i, BASE_URL);
    }
    return absoluteUrl(url);
}

function cleanText(text) {
    return (text || "")
        .replace(/\u00a0/g, " ")
        .replace(/\s+/g, " ")
        .trim();
}

function cleanHtml(html) {
    return (html || "")
        .replace(/<!--[\s\S]*?-->/g, "")
        .replace(/<a[^>]*>(.*?)<\/a>/gi, "$1")
        .replace(/&nbsp;/gi, " ")
        .replace(/(<br\s*\/?>\s*){3,}/gi, "<br><br>")
        .trim();
}

function stripAccents(text) {
    try {
        return (text || "").normalize("NFD").replace(/[\u0300-\u036f]/g, "");
    } catch (error) {
        return text || "";
    }
}

function isOngoingStatus(text) {
    var normalized = cleanText(stripAccents(String(text || "").toLowerCase()));
    if (!normalized) return true;

    return normalized.indexOf("hoan thanh") === -1 &&
        normalized.indexOf("da hoan thanh") === -1 &&
        normalized.indexOf("tron bo") === -1 &&
        normalized.indexOf("full") === -1;
}
