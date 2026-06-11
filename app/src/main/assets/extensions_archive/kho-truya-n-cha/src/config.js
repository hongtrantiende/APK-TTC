const BASE_URL = "https://khotruyenchu.me";
const API_BASE = "https://api.khotruyenchu.me/api/v1";
const DEFAULT_COVER = BASE_URL + "/khotruyenchu_logo.png";

function ktcTrim(text) {
    if (text === null || typeof text === "undefined") return "";
    return ("" + text).replace(/^\s+|\s+$/g, "");
}

function ktcToAbsolute(url) {
    let raw = ktcTrim(url);
    if (!raw) return "";
    if (raw.indexOf("http://") === 0 || raw.indexOf("https://") === 0) return raw;
    if (raw.indexOf("//") === 0) return "https:" + raw;
    return BASE_URL + (raw.charAt(0) === "/" ? "" : "/") + raw;
}

function ktcNormalizeUrl(url) {
    let raw = ktcTrim(url);
    if (!raw) return BASE_URL;
    return ktcToAbsolute(raw);
}

function ktcDecodeHtml(html) {
    let raw = ktcTrim(html);
    if (!raw) return "";
    try { return ktcTrim(Html.parse("<div>" + raw + "</div>").text()); } catch (error) {}
    return raw
        .replace(/&nbsp;/gi, " ")
        .replace(/&amp;/gi, "&")
        .replace(/&quot;/gi, "\"")
        .replace(/&#39;/gi, "'")
        .replace(/&lt;/gi, "<")
        .replace(/&gt;/gi, ">");
}

function ktcPlainText(html) {
    return ktcTrim(ktcDecodeHtml(html).replace(/\s+/g, " "));
}

function ktcShortText(text, maxLen) {
    let value = ktcPlainText(text);
    let limit = parseInt(maxLen || "220", 10);
    if (!value || !limit || value.length <= limit) return value;
    let cut = value.substring(0, limit);
    let lastSpace = cut.lastIndexOf(" ");
    if (lastSpace > 60) cut = cut.substring(0, lastSpace);
    return ktcTrim(cut) + "...";
}

function ktcResolveCover(coverUrl) {
    if (!coverUrl) return DEFAULT_COVER;
    return ktcToAbsolute(coverUrl);
}

function ktcStorySlug(url) {
    let m = ktcNormalizeUrl(url).match(/\/truyen\/([^\/?#]+)/i);
    return m && m[1] ? m[1] : "";
}

function ktcChapterId(url) {
    let m = ktcNormalizeUrl(url).match(/\/chuong-(\d+)/i);
    return m && m[1] ? m[1] : "";
}
