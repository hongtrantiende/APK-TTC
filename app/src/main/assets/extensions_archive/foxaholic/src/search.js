load('config.js');

function cleanAjaxTitle(title) {
    return stripHtml(title || "").replace(/\s+-\s+Foxaholic$/i, "");
}

function ajaxSearch(key) {
    try {
        let response = request(BASE_URL + "/wp-admin/admin-ajax.php", {
            method: "POST",
            headers: {
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8",
                "Origin": BASE_URL,
                "Referer": BASE_URL + "/",
                "X-Requested-With": "XMLHttpRequest"
            },
            body: {
                action: "wp-manga-search-manga",
                title: key || ""
            }
        });
        if (response && response.ok) {
            let text = response.text() || "";
            if (text) return JSON.parse(text);
        }
    } catch (error) {
    }
    return null;
}

function searchUrl(key, page) {
    let url = BASE_URL + "/?s=" + encodeURIComponent(key || "") + "&post_type=wp-manga";
    if (page && page !== "1") url += "&paged=" + encodeURIComponent(page);
    return url;
}

function searchItemDescription(e) {
    let parts = [];
    e.select(".list-chapter a[href], .chapter a[href], .wp-manga-chapter a[href], a[href*='/chapter-']").forEach(function(a) {
        let href = a.attr("href");
        let text = cleanText(a.text());
        if (text && isChapterUrl(href) && parts.length < 2) parts.push(text);
    });
    return parts.join(" - ");
}

function fallbackSearch(key, page) {
    let target = searchUrl(key, page);
    let doc = getDoc(target);
    if (!doc) return {data: [], next: ""};

    let data = [];
    let seen = {};
    let nodes = doc.select(".page-item-detail, .c-tabs-item__content, .row.c-tabs-item__content, .manga__item, .manga-item");
    nodes.forEach(function(e) {
        let links = e.select("a[href]");
        let linkNode = null;
        for (let i = 0; i < links.size(); i++) {
            if (isStoryUrl(links.get(i).attr("href"))) {
                linkNode = links.get(i);
                break;
            }
        }
        if (!linkNode) return;
        let link = normalizeUrl(linkNode.attr("href"));
        if (seen[link]) return;
        let imgNode = e.select(".item-thumb img, .summary_image img, .tab-thumb img, img").first();
        let name = firstText(e, [".post-title a", ".manga-title a", ".item-title a", "h3 a", "h4 a"]) || cleanText(linkNode.attr("title") || linkNode.text());
        if (!name) return;
        seen[link] = true;
        data.push({name: name, link: link, cover: imageFromNode(imgNode), description: searchItemDescription(e), host: BASE_URL});
    });

    if (data.length === 0) {
        doc.select("a[href*='/novel/']").forEach(function(a) {
            let href = a.attr("href");
            if (!isStoryUrl(href)) return;
            let link = normalizeUrl(href);
            if (seen[link]) return;
            let name = cleanText(a.attr("title") || a.text());
            if (!name || foldText(name).indexOf("chapter") >= 0) return;
            seen[link] = true;
            data.push({name: name, link: link, cover: "", description: "", host: BASE_URL});
        });
    }

    return {data: data, next: nextPage(doc, target)};
}

function execute(key, page) {
    if (!page || page === "1") {
        let result = ajaxSearch(key);
        let data = [];
        let seen = {};
        if (result && result.success && result.data) {
            for (let i = 0; i < result.data.length; i++) {
                let item = result.data[i];
                let link = normalizeUrl(item.url || "");
                if (!link || !isStoryUrl(link) || seen[link]) continue;
                seen[link] = true;
                data.push({
                    name: cleanAjaxTitle(item.title || ""),
                    link: link,
                    cover: normalizeImage(item.thumb || item.thumbnail || ""),
                    description: stripHtml(item.type || ""),
                    host: BASE_URL
                });
            }
        }
        if (data.length > 0) return Response.success(data, "");
    }

    let fallback = fallbackSearch(key, page);
    return Response.success(fallback.data, fallback.next);
}
