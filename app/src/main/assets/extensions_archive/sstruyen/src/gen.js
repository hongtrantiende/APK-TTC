load('config.js');

function execute(url, page) {
    url = normalizeUrl(url);
    if (page && page !== "1") {
        url = url + (url.indexOf("?") >= 0 ? "&" : "?") + "page=" + page;
    }

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let data = [];

        doc.select(".truyen-list .item").forEach(function (e) {
            let title = e.select("h3 a").first();
            if (!title) return;

            let cover = e.select("a.cover img, img").first();
            let author = e.select("a[href*='/tac-gia/']").first();
            let chapters = "";

            e.select("p.line").forEach(function (line) {
                let text = cleanText(line.text());
                if (text.indexOf("So chuong") >= 0 || text.indexOf("Số chương") >= 0) chapters = text;
            });

            data.push({
                name: cleanText(title.text()),
                link: normalizeUrl(title.attr("href")),
                cover: cover ? normalizeUrl(cover.attr("data-src") || cover.attr("src")) : "",
                description: author ? "Tac gia: " + cleanText(author.text()) + (chapters ? " - " + chapters : "") : chapters,
                host: BASE_URL
            });
        });

        let next = null;
        let current = page ? parseInt(page) : 1;
        doc.select(".phan-trang a[href]").forEach(function (e) {
            let href = e.attr("href");
            let text = cleanText(e.text());
            let match = href ? href.match(/[?&]page=(\d+)/) : null;
            if (match && text !== "Cuoi") {
                let pageNum = parseInt(match[1]);
                if (pageNum > current && (!next || pageNum < parseInt(next))) next = "" + pageNum;
            }
        });

        return Response.success(data, next);
    }

    return null;
}
