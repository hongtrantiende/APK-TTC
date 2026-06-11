load("config.js");

function execute(url) {
    let doc = fetchDoc(url);
    if (!doc) return Response.error("Cannot load chapter.");

    let contentEl = doc.select("div.vntq-text-content").first();
    if (!contentEl) return Response.error("Cannot find chapter content.");

    contentEl.select("script, style, ins.adsbygoogle, iframe, .adsbygoogle").remove();
    let html = contentEl.html();

    html = html.replace(/<nav[\s\S]*?<\/nav>/gi, "");

    return Response.success(html);
}