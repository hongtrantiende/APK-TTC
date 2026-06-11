load("config.js");

function toAbsolute(url) {
    if (!url) return "";
    if (url.startsWith("http")) return url;
    if (url.startsWith("//")) return "https:" + url;
    return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
}

function parseQueryParams(u) {
    let q = {};
    try {
        const qs = u.split("?")[1] || "";
        if (!qs) return q;
        qs.split("&").forEach(p => {
            if (!p) return;
            const kv = p.split("=");
            const k = decodeURIComponent(kv[0] || "").trim();
            const v = decodeURIComponent(kv.slice(1).join("=") || "");
            if (k) q[k] = v;
        });
    } catch (e) {}
    return q;
}

function buildApiUrl(inputUrl, page) {
    const DEFAULT_SORT = "update_date";
    let params = { sort: DEFAULT_SORT };
    // If input already has query, pick relevant params
    const existing = parseQueryParams(inputUrl || "");
    // carry through sort/q/keyword if present
    if (existing.sort) params.sort = existing.sort;
    if (existing.q) params.q = existing.q;
    if (existing.keyword) params.keyword = existing.keyword;
    if (existing.search) params.search = existing.search;

    params.page = String(page || 1);

    // If input already points to api/search-novels, preserve additional params
    let base = BASE_URL + "/api/search-novels";
    if ((inputUrl || "").indexOf("/api/search-novels") !== -1) {
        base = toAbsolute(inputUrl.split("?")[0]);
        // merge any other params from existing query
        for (let k in existing) {
            if (k === "page") continue; // replace with our page
            params[k] = existing[k];
        }
    }

    const query = Object.keys(params)
        .filter(k => params[k] !== undefined && params[k] !== null && params[k] !== "")
        .map(k => encodeURIComponent(k) + "=" + encodeURIComponent(params[k]))
        .join("&");
    return base + "?" + query;
}

function safeJson(res) {
    try {
        if (res && typeof res.json === "function") return res.json();
    } catch (e) {}
    try {
        const t = res ? res.text() : "";
        return t ? JSON.parse(t) : null;
    } catch (e) {
        return null;
    }
}

function pickArray(data) {
    if (!data) return [];
    if (Array.isArray(data)) return data;
    if (Array.isArray(data.novels)) return data.novels; // API shape
    if (Array.isArray(data.items)) return data.items;
    if (Array.isArray(data.data)) return data.data;
    if (Array.isArray(data.results)) return data.results;
    return [];
}

function toBook(item) {
    if (!item) return null;
    const title = item.title || item.name || item.novel_title || "";
    // API returns `url` as slug in sample; use it directly when it looks like a slug
    let link = "";
    const urlField = (item.url || "") + "";
    const slug = item.slug || item.novel_slug || "";
    if (urlField && !urlField.startsWith("http") && !urlField.startsWith("/")) {
        link = BASE_URL + "/truyen/" + urlField;
    } else if (slug && slug.indexOf("/") === -1) {
        link = BASE_URL + "/truyen/" + slug;
    } else if (urlField) {
        link = toAbsolute(urlField);
    }
    const coverRaw = item.thumbnail || item.cover || item.cover_url || item.image || "";
    const cover = toAbsolute(coverRaw);

    let meta = [];
    const author = item.author || item.authors || item.novel_author;
    if (author) meta.push(("" + author).toString());
    if (item.genre_name) meta.push(("" + item.genre_name).toString());
    const status = item.status || item.novel_status;
    if (status) meta.push(("" + status).toString());
    if (item.max_chapters) meta.push("Chương: " + item.max_chapters);
    const latest = item.latest_chapter || item.last_chapter || item.newest_chapter;
    if (latest) meta.push(("Chương mới: " + latest).toString());
    const description = meta.join(" · ");

    if (!title || !link) return null;
    return { name: title, link: toAbsolute(link), cover: cover, description: description, host: BASE_URL };
}

function execute(url, page) {
    const curPage = parseInt(page || "1");
    const apiUrl = buildApiUrl(url || "", curPage);
    const res = fetch(apiUrl);
    if (!res || !res.ok) return Response.success([]);

    const json = safeJson(res);
    const arr = pickArray(json);

    const books = (arr && arr.length) ? arr.map(toBook).filter(Boolean) : [];

    // Determine next page
    let nextPage = "";
    if (json && (json.total_pages || json.last_page || json.pagination)) {
        const current = json.current_page || json.page || curPage;
        const last = json.last_page || json.total_pages || (json.pagination ? json.pagination.total_pages : undefined);
        if (last && current < last) nextPage = String(current + 1);
    } else if (books.length > 0) {
        // Fallback: keep increasing until empty result
        nextPage = String(curPage + 1);
    }

    return Response.success(books, nextPage);
}