load('config.js');

function cleanChapterHtml(html) {
    html = html || "";
    html = html.replace(/<script[\s\S]*?<\/script>/gi, "");
    html = html.replace(/<ins[\s\S]*?<\/ins>/gi, "");
    html = html.replace(/\s*(style|class|id|onclick)="[^"]*"/gi, "");
    return html.trim();
}

function execute(url) {
    let doc = getDoc(url);
    if (!doc) return null;

    let title = firstText(doc, [".current-chapter", "h2.current-chapter", "h1"]);
    let content = firstHtml(doc, [".truyen", "#vungdoc .truyen", ".chapter-content", ".entry-content"]);
    content = cleanChapterHtml(content);
    if (!content) return Response.error("Khong tim thay noi dung chuong.");
    return Response.success((title ? "<h2>" + title + "</h2>\n" : "") + content);
}
