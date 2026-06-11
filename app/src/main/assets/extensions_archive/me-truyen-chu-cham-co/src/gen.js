function execute(url, page) {

    if (!page) page = "1";

    // ===== BUILD URL =====
    let fetchUrl;

    if (page === "1") {
        fetchUrl = url;
    } else {
        // luôn đảm bảo đúng format: ?page=2&status=...
        if (url.includes("?")) {
            let base = url.split("?")[0];
            let params = url.split("?")[1];

            // giữ lại status
            let statusMatch = params.match(/status=([^&]+)/);
            let status = statusMatch ? statusMatch[1] : null;

            fetchUrl = base + "?page=" + page + (status ? "&status=" + status : "");
        } else {
            fetchUrl = url + "?page=" + page;
        }
    }

    let res = fetch(fetchUrl);
    if (!res.ok) return null;

    let doc = res.html();
    let list = [];
    let seen = {};

    // ===== PARSE TRUYỆN =====
    let items = doc.select("div.flex.flex-row.items-start.gap-4");

    for (let i = 0; i < items.size(); i++) {
        let item = items.get(i);

        let linkEl = item.select("a[href^=/truyen/]").first();
        if (!linkEl) continue;

        let href = linkEl.attr("href");
        let fullUrl = "https://metruyenchu.co" + href;

        if (seen[fullUrl]) continue;
        seen[fullUrl] = true;

        let name = item.select("p").first().text();
        let cover = item.select("img").first().attr("src");
        let description = item.select("span.line-clamp-2").text();
        let author = item.select("a[href^=/tac-gia/] span").text();

        list.push({
            name: name,
            link: fullUrl,
            cover: cover,
            description: description,
            author: author
        });
    }

    // ===== NEXT PAGE =====
    let next = (parseInt(page) + 1).toString();

    // nếu hết data thì dừng
    if (list.length === 0) next = null;

    return Response.success(list, next);
}