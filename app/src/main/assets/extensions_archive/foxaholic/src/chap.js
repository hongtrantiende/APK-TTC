load('config.js');

function cleanChapterHtml(html) {
    html = html || "";
    html = html.replace(/<script[\s\S]*?<\/script>/gi, "");
    html = html.replace(/<style[\s\S]*?<\/style>/gi, "");
    html = html.replace(/<iframe[\s\S]*?<\/iframe>/gi, "");
    html = html.replace(/<form[\s\S]*?<\/form>/gi, "");
    html = html.replace(/<select[\s\S]*?<\/select>/gi, "");
    html = html.replace(/<button[\s\S]*?<\/button>/gi, "");
    html = html.replace(/<input[^>]*>/gi, "");
    html = html.replace(/<ins[\s\S]*?<\/ins>/gi, "");
    html = html.replace(/<div[^>]+(?:adsbygoogle|code-block|sharedaddy|nav-links|wp-manga-nav)[\s\S]*?<\/div>/gi, "");
    html = html.replace(/\s*(style|class|id|onclick|onload|data-[a-z0-9_-]+)="[^"]*"/gi, "");
    return html.trim();
}

function execute(url) {
    let doc = getDoc(url);
    if (!doc) return loadError();

    let title = firstText(doc, [".chapter-heading", ".reading-heading h1", ".entry-title", "h1"]);
    let content = firstHtml(doc, [
        ".reading-content .text-left",
        ".text-left",
        ".chapter-content",
        ".entry-content .reading-content",
        ".reading-content",
        ".cha-words",
        ".entry-content"
    ]);
    content = cleanChapterHtml(content);

    if (!content || cleanText(content).length < 20) return Response.error("Khong tim thay noi dung chapter.");
    return Response.success((title ? "<h2>" + title + "</h2>\n" : "") + content);
}
