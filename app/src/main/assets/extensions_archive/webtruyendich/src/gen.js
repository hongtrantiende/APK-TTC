load("config.js");

function toAbsolute(url) {
    if (!url) return "";
    if (url.startsWith("http")) return url;
    if (url.startsWith("//")) return "https:" + url;
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function setPageParam(u, page) {
    let abs = u;
    if (!abs.startsWith("http")) {
        abs = BASE_URL + (u.startsWith("/") ? "" : "/") + u;
    }
    if (!page) return abs;
    if (abs.indexOf("?") === -1) return abs + "?page=" + page;
    if (/([?&])page=\d+/.test(abs)) return abs.replace(/([?&])page=\d+/, "$1page=" + page);
    return abs + (abs.endsWith("?") || abs.endsWith("&") ? "" : "&") + "page=" + page;
}

function parseBooks(doc) {
    let list = [];
    // Cards as in index.html
    doc.select(".novel-grid .novel-card").forEach(card => {
        let a = card.select("a[href]").first();
        if (!a) return;
        let link = a.attr("href") || "";
        let img = card.select("img").first();
        let titleEl = card.select("h2").first();

        let title = titleEl ? titleEl.text() : "";
        if (!title && img) title = img.attr("alt") || "";
        if (!title) title = a.attr("title") || a.text();

        let cover = img ? (img.attr("data-src") || img.attr("data-original") || img.attr("src") || "") : "";

        // Description from the two spans (chapters, genre)
        let spans = card.select(".text-white.text-xs span");
        let meta = [];
        if (spans && spans.size && spans.size() > 0) {
            for (let i = 0; i < spans.size(); i++) {
                let t = (spans.get(i).text() || "").trim();
                if (t) meta.push(t);
            }
        }
        let description = meta.join(" · ");

        // Normalize links
        link = toAbsolute(link);
        cover = toAbsolute(cover);

        if (title && link) {
            list.push({
                name: title,
                link: link,
                cover: cover,
                description: description,
                host: BASE_URL
            });
        }
    });

    // Fallback: some list pages may use a different markup
    if (list.length === 0) {
        doc.select(".result-item").forEach(e => {
            let a = e.select("h3 a").first();
            if (!a) return;
            let img = e.select("img").first();
            let title = a.text();
            let link = toAbsolute(a.attr("href"));
            let cover = img ? toAbsolute(img.attr("src")) : "";
            let desc = e.select("p").get(1);
            let description = desc ? desc.text() : "";
            list.push({ name: title, link: link, cover: cover, description: description, host: BASE_URL });
        });
    }

    return list;
}

function computeNextPage(doc, currentPage) {
    const cur = parseInt(currentPage || "1");
    const pageFromHref = (href) => {
        if (!href) return NaN;
        const m = href.match(/(?:[?&]|\/)page=(\d+)/);
        return m ? parseInt(m[1]) : NaN;
    };

    // 1) Prefer explicit chevron_right button
    let rightHref = "";
    doc.select("a").forEach(a => {
        if (rightHref) return;
        const span = a.select("span.material-symbols-outlined").first();
        if (span) {
            const t = (span.text() || "").trim();
            if (t === "chevron_right") {
                rightHref = a.attr("href") || "";
            }
        }
    });
    let p = pageFromHref(rightHref);
    if (!isNaN(p) && p > cur) return String(p);

    // 2) Find active page and then +1
    let active = doc.select(".space-x-1 a.bg-yellow-500, .space-x-1 a[aria-current='page'], a.active, a.current").first();
    let activeNum = NaN;
    if (active) {
        activeNum = parseInt((active.text() || "").trim());
        if (isNaN(activeNum)) activeNum = pageFromHref(active.attr("href"));
        const target = (isNaN(activeNum) ? cur : activeNum) + 1;
        let found = NaN;
        doc.select(".space-x-1 a").forEach(a => {
            const txt = parseInt((a.text() || "").trim());
            if (isNaN(found) && !isNaN(txt) && txt === target) found = txt;
            const hp = pageFromHref(a.attr("href"));
            if (isNaN(found) && !isNaN(hp) && hp === target) found = hp;
        });
        if (!isNaN(found) && found > cur) return String(found);
    }

    // 3) Minimal page number > current from hrefs
    let best = Number.POSITIVE_INFINITY;
    doc.select('a[href*="?page="]').forEach(a => {
        const n = pageFromHref(a.attr("href"));
        if (!isNaN(n) && n > cur && n < best) best = n;
    });
    if (best !== Number.POSITIVE_INFINITY) return String(best);

    // 4) Fallback: numeric text links
    let bestText = Number.POSITIVE_INFINITY;
    doc.select(".space-x-1 a").forEach(a => {
        const n = parseInt((a.text() || "").trim());
        if (!isNaN(n) && n > cur && n < bestText) bestText = n;
    });
    if (bestText !== Number.POSITIVE_INFINITY) return String(bestText);

    return "";
}

function execute(url, page) {
    if (!page) page = "1";

    const pageUrl = setPageParam(url, page);
    const res = fetch(pageUrl);
    if (!res || !res.ok) return Response.success([]);

    const doc = res.html();
    const books = parseBooks(doc);
    const nextPage = books.length ? computeNextPage(doc, page) : "";

    return Response.success(books, nextPage);
}