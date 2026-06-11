var BASE_URL = "https://xtruyen.vn";

function normalizeUrl(url) {
    if (!url) return BASE_URL;
    if (url.startsWith("//")) return "https:" + url;
    if (url.startsWith("/")) return BASE_URL + url;
    if (!url.startsWith("http")) return BASE_URL + "/" + url;
    return url;
}

function cleanText(text) {
    return text ? text.replace(/\s+/g, " ").trim() : "";
}
