load("config.js");

function execute(url) {
    let doc = fetchDoc(url);
    if (!doc) return Response.error("Cannot load detail page.");

    let name = cleanText(doc.select("h1.vntq-reader-title").first() ? doc.select("h1.vntq-reader-title").first().text() : "");
    if (!name) return Response.error("Cannot find novel title.");

    let coverEl = doc.select("div.vntq-large-cover-box img").first();
    let cover = coverEl ? toAbsolute(coverEl.attr("src") || "") : "";

    let authorEl = doc.select("div.vntq-author-top a").first();
    let author = authorEl ? cleanText(authorEl.text()) : "Unknown";

    let ongoing = true;
    let detail = "";
    let statusEl = doc.select(".vntq-stats-info").first();
    if (statusEl) {
        let st = cleanText(statusEl.text()).toLowerCase();
        if (st.indexOf("hoàn thành") > -1) { ongoing = false; detail = "Status: Hoàn Thành<br>"; }
        else if (st.indexOf("đang ra") > -1) { ongoing = true; detail = "Status: Đang Ra<br>"; }
    }

    return Response.success({
        name: name,
        cover: cover,
        host: BASE_URL,
        author: author,
        description: "",
        detail: detail,
        ongoing: ongoing
    });
}