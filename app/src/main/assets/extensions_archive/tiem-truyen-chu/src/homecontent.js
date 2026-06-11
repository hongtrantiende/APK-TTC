load('config.js');

function execute(url, page) {
    var requestUrl = BASE_URL;

    if (url === "new") {
        requestUrl = BASE_URL + "/danh-sach?sort=update";
    } else if (url === "newest") {
        requestUrl = BASE_URL + "/danh-sach?sort=new";
    } else {
        // editor's choice - homepage
        requestUrl = BASE_URL;
    }

    if (page && url !== "editor") {
        if (requestUrl.indexOf("?") !== -1) {
            requestUrl = requestUrl + "&page=" + page;
        } else {
            requestUrl = requestUrl + "?page=" + page;
        }
    }

    let response = fetch(requestUrl);
    if (response.ok) {
        let doc = response.html();
        let novelList = [];

        let itemEls = doc.select(".story-item");
        if (itemEls.size() === 0) {
            itemEls = doc.select(".top-1-card, a[href*='/truyen/'].text-decoration-none.d-flex, a[href*='/truyen/'].text-decoration-none.d-block");
        }

        itemEls.forEach(function (e) {
            let titleEl = e.select(".story-title, .top-1-title, .rank-title, a[href*='/truyen/'].fw-bold, a[href*='/truyen/']").first();
            if (!titleEl) {
                titleEl = e.attr("href") && e.attr("href").indexOf("/truyen/") !== -1 ? e : e.select("a").first();
            }
            if (!titleEl) return;

            let link = "";
            let linkEl = e.select("a[href*='/truyen/']").first();
            if (linkEl) {
                link = linkEl.attr("href");
            } else {
                link = titleEl.attr("href");
            }

            let coverEl = e.select("img.story-poster, img.top-1-img, img[src*='/stories/']").first();
            if (!coverEl) {
                coverEl = e.select("img").first();
            }
            let cover = "";
            if (coverEl) {
                cover = coverEl.attr("data-src");
                if (!cover) cover = coverEl.attr("src");
            }

            let desc = "";
            let metaEl = e.select(".story-meta, .small.text-muted, .text-muted").first();
            if (metaEl) desc = metaEl.text();

            novelList.push({
                name: titleEl.text().trim(),
                link: link,
                cover: cover,
                description: desc,
                host: BASE_URL
            });
        });

        // Pagination for list pages
        let next = null;
        if (url !== "editor") {
            let currentPage = page ? parseInt(page) : 1;
            let pageLinks = doc.select(".pagination .page-link, .pagination a");
            if (pageLinks.size() > 0) {
                let lastLink = pageLinks.last();
                let href = lastLink.attr("href");
                if (href && href.indexOf("page=") !== -1) {
                    let match = href.match(/page=(\d+)/);
                    if (match && parseInt(match[1]) > currentPage) {
                        next = "" + (currentPage + 1);
                    }
                }
            }
        }

        return Response.success(novelList, next);
    }

    return null;
}
