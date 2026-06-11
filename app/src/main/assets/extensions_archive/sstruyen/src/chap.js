load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let content = doc.select(".truyen").first();
        if (!content) content = doc.select(".vung-doc").first();
        if (!content) content = doc.select(".chapter-content, #chapter-content, .content").first();

        if (content) {
            content.select("script, iframe, ins, style").remove();
            content.select(".chapter-title, .chapter_control, .chapter_wrap, .line-control, .chap-info").remove();
            content.select("#download-book, .btn-dschuong, .back, .next").remove();
            return Response.success(content.html());
        }
    }

    return null;
}
