load("config.js");

function toAbsolute(url) {
    if (!url) return "";
    if (url.startsWith("http")) return url;
    if (url.startsWith("//")) return "https:" + url;
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function fetchDoc(url) {
    let response = fetch(url);
    if (response && response.ok) {
        return response.html();
    }

    if (response && (response.status === 400 || response.status === 401 || response.status === 403 || response.status === 503)) {
        let browser = Engine.newBrowser();
        browser.launch(url, 7000);
        let doc = browser.html();
        browser.close();
        return doc;
    }

    return null;
}

function cleanText(text) {
    return (text || "").replace(/\s+/g, " ").trim();
}

function parseList(doc) {
    let list = [];
    let items = doc.select(".book-img-text > ul > li");
    items.forEach(item => {
        let titleEl = item.select(".book-mid-info h4 a").first();
        if (!titleEl) titleEl = item.select("h4 a[href*='/doc-truyen/']").first();
        if (!titleEl) return;

        let name = cleanText(titleEl.text());
        let link = toAbsolute(titleEl.attr("href"));
        if (!name || !link) return;

        let coverEl = item.select(".book-img-box img").first();
        if (!coverEl) coverEl = item.select("img").first();
        let cover = "";
        if (coverEl) cover = coverEl.attr("data-src") || coverEl.attr("src") || "";

        let author = cleanText(item.select("p.author").text());
        let update = cleanText(item.select("p.update").text());
        let intro = cleanText(item.select("p.intro").text());
        if (intro.length > 180) intro = intro.substring(0, 177) + "...";

        let descParts = [];
        if (author) descParts.push(author);
        if (update) descParts.push(update);
        if (intro) descParts.push(intro);

        list.push({
            name: name,
            link: link,
            cover: toAbsolute(cover),
            description: descParts.join(" | "),
            host: BASE_URL
        });
    });
    return list;
}

function parseNext(doc, page) {
    let current = parseInt(page || "1");
    if (doc.select("ul.pagination a[rel=next]").size() > 0) {
        return String(current + 1);
    }
    return "";
}

function parseFromAutocomplete(key) {
    let response = fetch(BASE_URL + "/tim-kiem?term=" + encodeURIComponent(key));
    if (!response || !response.ok) return [];

    let data = null;
    try {
        data = response.json();
    } catch (e) {
        return [];
    }

    if (!data || !data.length) return [];

    let list = [];
    data.forEach(item => {
        if (item.type !== "story") return;
        if (item.story_type && String(item.story_type) === "1") return;
        if (!item.url) return;

        list.push({
            name: cleanText(item.name || ""),
            link: toAbsolute("/doc-truyen/" + item.url),
            cover: "",
            description: "Goi y tim kiem",
            host: BASE_URL
        });
    });

    return list;
}

function execute(key, page) {
    if (!page) page = "1";

    let url = BASE_URL + "/ket-qua-tim-kiem?term=" + encodeURIComponent(key) + "&page=" + page;
    let doc = fetchDoc(url);
    if (doc) {
        let list = parseList(doc);
        if (list.length > 0) {
            let next = parseNext(doc, page);
            return Response.success(list, next);
        }
    }

    let fallback = parseFromAutocomplete(key);
    return Response.success(fallback);
}
