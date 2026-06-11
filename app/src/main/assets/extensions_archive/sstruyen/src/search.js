load('config.js');

function execute(key, page) {
    let url = BASE_URL + "/tim-kiem?s=" + encodeURIComponent(key);
    if (page && page !== "1") url += "&page=" + page;
    return executeSearch(url, page);
}

function executeSearch(url, page) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let data = [];

        doc.select(".truyen-list .item").forEach(function (e) {
            let title = e.select("h3 a").first();
            if (!title) return;

            let cover = e.select("a.cover img, img").first();
            let author = e.select("a[href*='/tac-gia/']").first();

            data.push({
                name: cleanText(title.text()),
                link: normalizeUrl(title.attr("href")),
                cover: cover ? normalizeUrl(cover.attr("data-src") || cover.attr("src")) : "",
                description: author ? "Tac gia: " + cleanText(author.text()) : "",
                host: BASE_URL
            });
        });

        let next = null;
        let current = page ? parseInt(page) : 1;
        doc.select(".phan-trang a[href]").forEach(function (e) {
            let href = e.attr("href");
            let match = href ? href.match(/[?&]page=(\d+)/) : null;
            if (match) {
                let pageNum = parseInt(match[1]);
                if (pageNum > current && (!next || pageNum < parseInt(next))) next = "" + pageNum;
            }
        });

        return Response.success(data, next);
    }

    return null;
}
