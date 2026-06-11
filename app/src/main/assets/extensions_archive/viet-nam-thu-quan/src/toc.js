load("config.js");

function execute(url) {
    let doc = fetchDoc(url);
    if (!doc) return Response.error("Cannot load chapter list.");

    let chapters = [];
    let selectEl = doc.select("select.vntq-select-chuong").first();
    if (!selectEl) return Response.error("Cannot find chapter select.");

    let baseUrl = url.replace(/\/+$/, "");
    selectEl.select("option").forEach(opt => {
        let val = ("" + opt.attr("value")).trim();
        if (!val) return;
        let chapterName = cleanText(opt.text());
        if (!chapterName) return;

        chapters.push({
            name: chapterName,
            url: baseUrl + "/chuong-" + val,
            host: BASE_URL
        });
    });

    if (chapters.length === 0) return Response.error("Cannot find chapter list.");
    return Response.success(chapters);
}