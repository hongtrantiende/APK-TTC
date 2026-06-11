load('config.js');

function execute(key, page) {
    let url = BASE_URL + "/?s=" + encodeURIComponent(key) + "&post_type=wp-manga";
    if (page && page !== "1") url += "&page=" + page;
    return executeSearch(url, page);
}

function executeSearch(url, page) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let data = [];

        doc.select(".c-tabs-item").forEach(function (e) {
            let title = e.select(".post-title h3 a").first();
            if (!title) return;

            let cover = e.select(".tab-thumb img").first();
            let desc = "";
            let status = e.select(".mg_status .summary-content").first();
            if (status) desc = "Trạng thái: " + cleanText(status.text());

            data.push({
                name: cleanText(title.text()),
                link: normalizeUrl(title.attr("href")),
                cover: cover ? normalizeUrl(cover.attr("src")) : "",
                description: desc,
                host: BASE_URL
            });
        });

        let next = null;
        let current = page ? parseInt(page) : 1;
        doc.select(".pagination a[href]").forEach(function (e) {
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
