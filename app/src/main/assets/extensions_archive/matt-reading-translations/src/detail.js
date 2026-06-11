load("config.js");

function execute(url) {
    let storyUrl = normalizeUrl(url);
    let doc = fetchDoc(storyUrl);
    if (!doc) return Response.error("Cannot load detail page.");

    let title = cleanText(doc.select("h1.entry-title").first() ? doc.select("h1.entry-title").first().text() : "");
    if (!title) return Response.error("Cannot find novel title.");

    let cover = "";
    let coverEl = doc.select("div.entry-content img").first();
    if (coverEl) cover = toAbsolute(coverEl.attr("src") || "");

    let description = "";
    let paras = doc.select("div.entry-content p");
    paras.forEach(p => {
        let img = p.select("img").first();
        if (img) return;
        let text = cleanText(p.text());
        if (!text) return;
        if (description) description += "<br><br>";
        description += text;
    });

    let statusText = cleanText(doc.text()).toLowerCase();
    let ongoing = false;
    if (statusText.indexOf("ongoing") > -1) ongoing = true;
    if (statusText.indexOf("complete") > -1) ongoing = false;
    if (statusText.indexOf("dropped") > -1) ongoing = false;

    let detail = "";
    if (statusText.indexOf("ongoing") > -1) detail += "Status: Ongoing<br>";
    else if (statusText.indexOf("complete") > -1) detail += "Status: Complete<br>";
    else if (statusText.indexOf("dropped") > -1) detail += "Status: Dropped<br>";

    return Response.success({
        name: title,
        cover: cover,
        host: BASE_URL,
        author: "MattReading",
        description: description,
        detail: detail,
        ongoing: ongoing
    });
}
