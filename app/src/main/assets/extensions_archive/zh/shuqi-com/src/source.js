load("config.js");

function ensureSpm(u) {
    if (!u) return "";
    const SPM = "aliwx.list_store.0.0";
    // preserve hash if present
    let hash = "";
    const hi = u.indexOf("#");
    if (hi !== -1) {
        hash = u.substring(hi);
        u = u.substring(0, hi);
    }
    if (u.indexOf("?") === -1) {
        u = u + "?spm=" + SPM;
    } else if (!/[?&]spm=/.test(u)) {
        u = u + (u.endsWith("&") || u.endsWith("?") ? "" : "&") + "spm=" + SPM;
    }
    return u + hash;
}

function buildPageUrl(inputUrl, page) {
    let absolute = inputUrl;
    if (!absolute.startsWith("http")) {
        absolute = BASE_URL + (inputUrl.startsWith("/") ? "" : "/") + inputUrl;
    }
    if (absolute.indexOf("?") === -1) {
        return absolute + "?page=" + page;
    }
    if (absolute.match(/([?&])page=\d+/)) {
        return absolute.replace(/([?&])page=\d+/, "$1page=" + page);
    }
    return absolute + (absolute.endsWith("?") || absolute.endsWith("&") ? "" : "&") + "page=" + page;
}

function parseBooks(doc) {
    let books = [];
    doc.select("ul.store-ul li").forEach(li => {
        let anchors = li.select('a[href*="/book/"]');
        let first = anchors.first();
        if (!first) return;

        let title = (first.attr("title") || first.text() || "").trim();
        let href = first.attr("href");

        if (!href || !title) return;

        // Extract cover (handles src, data-src, data-original, background-image)
        let cover = "";
        let img = li.select("img").first();
        if (img) {
            cover = (img.attr("data-src") || img.attr("data-original") || img.attr("data-echo") || img.attr("src") || "").trim();
        }
        if (!cover) {
            let coverEl = li.select(".book-img, .cover, .book-cover, .store-img, a[href*='/book/']").first();
            if (coverEl) {
                let style = (coverEl.attr("style") || "").trim();
                let m = style.match(/url\(['"]?(.*?)['"]?\)/i);
                if (m) cover = m[1];
            }
        }
        if (cover) {
            if (cover.startsWith("//")) {
                cover = "https:" + cover;
            } else if (!cover.startsWith("http")) {
                cover = (cover.startsWith("/") ? BASE_URL : BASE_URL + "/") + cover;
            }
        }

        let desc = "";
        if (anchors.size && anchors.size() > 1) {
            let second = anchors.get(1);
            if (second) desc = (second.attr("title") || "").trim();
        }

        // Prefer returning relative links so the app opens internally (avoids external window)
        if (!href) return;
        href = href.startsWith("http") ? href : (href.startsWith("/") ? href : "/" + href);
        // ensure detail links always carry required spm to avoid 404
        href = ensureSpm(href);

        books.push({
            name: title,
            link: href,
            cover: cover || "",
            description: desc,
            host: BASE_URL
        });
    });
    return books;
}

function hasNext(doc, currentPageNum) {
    let next = false;
    let n = parseInt(currentPageNum) + 1;
    doc.select(".comp-web-pages a, .store-paging a").forEach(a => {
        let t = (a.text() || "").trim();
        if (t === "下一页" || t === String(n)) next = true;
    });
    return next;
}

function execute(url, page) {
    if (!page) page = "1";

    let pageUrl = buildPageUrl(url, page);
    let response = fetch(pageUrl);
    if (!response || !response.ok) {
        return Response.success([]);
    }

    let doc = response.html();
    let books = parseBooks(doc);

    let nextPage = "";
    if (books.length > 0 && hasNext(doc, page)) {
        nextPage = String(parseInt(page) + 1);
    }

    return Response.success(books,nextPage);
}