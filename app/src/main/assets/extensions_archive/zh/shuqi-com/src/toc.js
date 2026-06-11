load("config.js");

function getQueryParam(u, key) {
    if (!u) return "";
    const qIndex = u.indexOf("?");
    const query = qIndex >= 0 ? u.substring(qIndex + 1) : "";
    if (!query) return "";
    const parts = query.split("&");
    for (let i = 0; i < parts.length; i++) {
        const kv = parts[i].split("=");
        if (decodeURIComponent(kv[0]) === key) return decodeURIComponent(kv[1] || "");
    }
    return "";
}

function ensureSpm(u, spm) {
    if (!u) return "";
    const SPM = spm || "aliwx.cover.0.0";
    // preserve hash
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

function toRelative(u) {
    if (!u) return "";
    // keep hash separate
    let hash = "";
    const hi = u.indexOf("#");
    if (hi !== -1) {
        hash = u.substring(hi);
        u = u.substring(0, hi);
    }
    // strip origin if it's shuqi
    if (/^https?:\/\//i.test(u) && /:\/\//.test(u)) {
        u = u.replace(/^https?:\/\/[^/]+/i, "");
    }
    if (!u.startsWith("/")) u = "/" + u;
    return u + hash;
}

function joinUrl(base, path) {
    const b = (base || "").replace(/\/$/, "");
    const p = (path || "").startsWith("/") ? path : "/" + (path || "");
    return b + p;
}

function buildChapterUrl(inputUrl) {
    // Normalize to /chapter?bid=... and ensure spm=aliwx.cover.0.0
    let url = inputUrl || "";
    let bid = getQueryParam(url, "bid");
    let spm = getQueryParam(url, "spm") || "aliwx.cover.0.0";

    // If it's a /book/{id}.html, convert to /chapter?bid={id}
    const m = url.match(/\/book\/(\d+)\.html/i);
    if (!bid && m) bid = m[1];

    if (!/\/chapter(\?|$)/.test(url)) {
        url = "/chapter";
    } else {
        // strip origin to make it relative
        url = toRelative(url);
        // strip path only, we'll rebuild query below
        const qpos = url.indexOf("?");
        if (qpos !== -1) url = url.substring(0, qpos);
    }

    let qs = [];
    if (bid) qs.push("bid=" + bid);
    let built = url + (qs.length ? ("?" + qs.join("&")) : "");
    built = ensureSpm(built, spm);
    return built;
}

function execute(url) {
    // Build correct chapter index URL and fetch
    const chapterUrl = buildChapterUrl(url);
    const fetchUrl = joinUrl(BASE_URL, chapterUrl); // absolute for fetch, return relative links below

    let response = fetch(fetchUrl);
    if (!response || !response.ok) {
        // Retry once with default spm enforced
        const retry = joinUrl(BASE_URL, ensureSpm(chapterUrl, "aliwx.cover.0.0"));
        response = fetch(retry);
        if (!response || !response.ok) {
            return Response.error("Không thể tải dữ liệu chương");
        }
    }

    let doc = response.html();
    let chapters = [];

    const seenCid = new Set();
    const seenHref = new Set();
    const seenTitle = new Set();
    function normTitle(t) { return (t || "").replace(/\s+/g, " ").trim(); }

    // Prefer anchors within the chapter table and only those containing cid parameter
    let anchors = doc.select("table.chapterul a[href*='cid='], .chapterul a[href*='cid=']");

    anchors.forEach(link => {
        let chapterTitle = normTitle(link.text() || link.attr("title") || link.attr("data-title") || "");
        let chapterHref = (link.attr("href") || "").trim();
        if (!chapterTitle || !chapterHref) return;
        if (chapterHref.startsWith("javascript") || chapterHref.startsWith("#")) return;

        // Make relative to keep it opening internally
        chapterHref = toRelative(chapterHref);

        // Ensure bid exists
        const pageBid = getQueryParam(chapterUrl, "bid");
        if (pageBid && !/[?&]bid=/.test(chapterHref)) {
            chapterHref += (chapterHref.indexOf("?") === -1 ? "?" : "&") + "bid=" + pageBid;
        }
        // Ensure spm exists (use same spm as the chapter page or default to cover)
        const pageSpm = getQueryParam(chapterUrl, "spm") || "aliwx.cover.0.0";
        chapterHref = ensureSpm(chapterHref, pageSpm);

        // Deduplicate by cid, href, or normalized title
        const cid = (function(u){ const m = (u||"").match(/[?&]cid=(\d+)/); return m ? m[1] : null; })(chapterHref);
        if (cid && seenCid.has(cid)) return;
        if (seenHref.has(chapterHref)) return;
        const tkey = chapterTitle.toLowerCase();
        if (seenTitle.has(tkey)) return;

        chapters.push({
            name: chapterTitle,
            url: chapterHref,
            host: BASE_URL
        });

        if (cid) seenCid.add(cid);
        seenHref.add(chapterHref);
        seenTitle.add(tkey);
    });

    // Sort by cid if available, else by chapter number in title
    function getCid(u) {
        const m = (u || "").match(/[?&]cid=(\d+)/);
        return m ? parseInt(m[1]) : null;
    }

    chapters.sort((a, b) => {
        const ca = getCid(a.url);
        const cb = getCid(b.url);
        if (ca != null && cb != null) return ca - cb;
        const aMatch = a.name.match(/第(\d+)章/);
        const bMatch = b.name.match(/第(\d+)章/);
        if (aMatch && bMatch) return parseInt(aMatch[1]) - parseInt(bMatch[1]);
        return 0;
    });

    return Response.success(chapters);
}