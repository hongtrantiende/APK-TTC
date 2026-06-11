load("config.js");

function trimSlash(s) {
    return s.replace(/\/+$/, "");
}
const BASE_ORIGIN = trimSlash(BASE_URL || "");

function joinUrl(base, path) {
    base = trimSlash(base || "");
    if (!path) return base;
    if (path.startsWith("http")) return path;
    if (!path.startsWith("/")) path = "/" + path;
    return base + path;
}

function toRelative(u) {
    if (!u) return u;
    const origin = BASE_ORIGIN;
    if (u.startsWith(origin)) return u.substring(origin.length) || "/";
    return u;
}

function textOf(el) {
    return el ? el.text().trim() : "";
}

function attrOf(el, name) {
    return el ? (el.attr(name) || "").trim() : "";
}

function buildSearchPath(key, page) {
    const p = page && page !== '1' ? page : '1';
    return "/search?keyword=" + encodeURIComponent(key) + "&page=" + p;
}

function parseNextPage(doc) {
    let nextPage = "";
    const links = doc.select(".store-paging .comp-web-pages a");
    for (let i = 0; i < links.size(); i++) {
        const a = links.get(i);
        const t = a.text();
        if (t && t.indexOf("下一页") !== -1) {
            const href = a.attr("href") || "";
            const m = href.match(/[?&]page=(\d+)/);
            if (m) nextPage = m[1];
            break;
        }
    }
    return nextPage;
}

function parseMatchBook(doc, seen) {
    const box = doc.select(".matchbook .view").first();
    if (!box) return null;
    const titleA = box.select(".bookTitle .bname").first();
    const coverImg = box.select("a.cover img").first();
    const authorEl = box.select(".bauthor").first();
    const catEl = box.select(".lastchapter li").first();
    const descEl = box.select(".bookDesc .js-descContent").first();

    let link = attrOf(titleA, "href") || attrOf(box.select("a.cover").first(), "href");
    if (!link) return null;
    const relLink = toRelative(link.startsWith("http") ? link : joinUrl(BASE_ORIGIN, link));
    if (seen[relLink]) return null;

    let name = textOf(titleA);
    let cover = attrOf(coverImg, "src");
    if (cover && !cover.startsWith("http")) cover = joinUrl(BASE_ORIGIN, cover);
    let author = textOf(authorEl).replace(/^作者：?/, "");
    let cat = textOf(catEl);
    let desc = textOf(descEl);

    const parts = [];
    if (author) parts.push(author);
    if (cat) parts.push(cat);
    if (desc) parts.push(desc);

    const item = {
        name: name,
        link: relLink,
        cover: cover,
        description: parts.join(" | "),
        host: BASE_URL
    };
    seen[relLink] = 1;
    return item;
}

function parseList(doc, seen) {
    const items = [];
    const lis = doc.select(".searchlist-main ul li");
    for (let i = 0; i < lis.size(); i++) {
        const li = lis.get(i);
        const titleA = li.select("a.ellipsis").first() || li.select("a.book-cover").first();
        const coverImg = li.select("a.book-cover img").first();
        const authorA = li.select("p.book-autor a").first();
        const authorP = li.select("p.book-autor").first();
        const catEl = li.select(".category").first();
        const descEl = li.select("p.book-newc").first();

        let link = attrOf(titleA, "href");
        if (!link) continue;
        const relLink = toRelative(link.startsWith("http") ? link : joinUrl(BASE_ORIGIN, link));
        if (seen[relLink]) continue;

        let name = textOf(titleA);
        let cover = attrOf(coverImg, "src");
        if (cover && !cover.startsWith("http")) cover = joinUrl(BASE_ORIGIN, cover);
        let author = textOf(authorA) || textOf(authorP).replace(/^作者：?/, "");
        let cat = textOf(catEl);
        let desc = textOf(descEl);

        const parts = [];
        if (author) parts.push(author);
        if (cat) parts.push(cat);
        if (desc) parts.push(desc);

        items.push({
            name: name,
            link: relLink,
            cover: cover,
            description: parts.join(" | "),
            host: BASE_URL
        });
        seen[relLink] = 1;
    }
    return items;
}

function execute(key, page) {
    if (!page) page = '1';
    const path = buildSearchPath(key, page);
    const url = joinUrl(BASE_ORIGIN, path);

    const response = fetch(url);
    if (!response.ok) return Response.error("Có lỗi khi tải dữ liệu");
    const doc = response.html();

    const seen = Object.create(null);
    const books = [];

    const top = parseMatchBook(doc, seen);
    if (top) books.push(top);

    const list = parseList(doc, seen);
    for (let i = 0; i < list.length; i++) books.push(list[i]);

    const nextPage = parseNextPage(doc);
    return Response.success(books, nextPage);
}