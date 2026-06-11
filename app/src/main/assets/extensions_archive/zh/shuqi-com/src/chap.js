load("config.js");

function addSpm(u) {
    if (!u) return "";
    return u.indexOf("spm=") === -1 ? u + (u.indexOf("?") === -1 ? "?" : "&") + "spm=aliwx.reader.0.0" : u;
}

function execute(url) {
    var u = addSpm(url);
    var browser = Engine.newBrowser();
    var doc = browser.launch(u, 8000);
    browser.close();

    // Shuqi renders content inside .chapter-content; fallback to .read-content
    var html = (doc.select(".chapter-content").html() || doc.select(".read-content").html() || "").trim();
    if (!html) {
        return Response.error("Không thể tải nội dung chương");
    }
    html = html.replace(/\&nbsp;/g, " ").replace(/<br\s*\/??>|\n/g, "<br><br>");
    return Response.success(html);
}