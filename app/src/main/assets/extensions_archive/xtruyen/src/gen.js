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

        doc.select(".popular-item-wrap").forEach(function (e) {
            let title = e.select(".widget-title a").first();
            if (!title) {
                title = e.select(".popular-img a").first();
            }
            if (!title) return;

            let cover = e.select(".popular-img img").first();
            let text = cleanText(title.text());
            if (!text) {
                text = cleanText(title.attr("title"));
            }

            data.push({
                name: text,
                link: normalizeUrl(title.attr("href")),
                cover: cover ? normalizeUrl(cover.attr("src")) : "",
                description: "",
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
