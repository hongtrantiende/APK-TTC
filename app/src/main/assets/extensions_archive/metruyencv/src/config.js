let BASE_URL = "https://metruyencv.com.vn";

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
