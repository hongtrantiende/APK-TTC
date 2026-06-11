function execute(url) {

    // ===== GET HTML =====
    let res = fetch(url);
    if (!res.ok) return null;

    let doc = res.html();

    // ===== LẤY BOOK ID (ưu tiên og:image) =====
    let bookId = null;

    let cover = doc.select("meta[property=og:image]").attr("content");
    if (cover) {
        let match = cover.match(/cover\/([a-f0-9]{24})/i);
        if (match) bookId = match[1];
    }

    // fallback nếu fail
    if (!bookId) {
        let html = res.text();
        let m = html.match(/"bookId":"([a-f0-9]{24})"/);
        if (m) bookId = m[1];
    }

    if (!bookId) return null;

    // ===== LẤY SLUG =====
    let bookSlug = url.split("/truyen/")[1].split("/")[0];

    // ===== BUILD BODY =====
    let body = JSON.stringify([{
        bookId: bookId,
        page: 1,
        limit: 1000,
        isNewest: false
    }]);

    // ===== BUILD next-router-state-tree (QUAN TRỌNG) =====
    let routerState = encodeURIComponent(JSON.stringify([
        "",
        {
            children: [
                "truyen",
                {
                    children: [
                        ["id", bookSlug, "d"],
                        {
                            children: ["__PAGE__", {}, null, null]
                        },
                        null,
                        null
                    ]
                },
                null,
                null
            ]
        },
        null,
        null,
        true
    ]));

    // ===== CALL API =====
    let api = fetch(url, {
        method: "POST",
        headers: {
            "accept": "text/x-component",
            "content-type": "text/plain;charset=UTF-8",

            // ⚠️ cái này vẫn có thể đổi, nhưng hiện tại dùng được
            "next-action": "404fe41c8fd89040dfda02d4aea144091fcefbf07e",

            "next-router-state-tree": routerState,

            "referer": url,
            "origin": "https://metruyenchu.co",
            "user-agent": "Mozilla/5.0"
        },
        body: body
    });

    if (!api.ok) return null;

    let text = api.text();

    // ===== PARSE DATA =====
    let jsonPart = text.split("\n").find(line => line.startsWith("1:"));
    if (!jsonPart) return null;

    let raw = JSON.parse(jsonPart.replace(/^1:/, ""));

    let data = null;

    if (Array.isArray(raw)) {
        data = raw;
    } else if (raw.data && Array.isArray(raw.data)) {
        data = raw.data;
    } else if (raw.chapters && Array.isArray(raw.chapters)) {
        data = raw.chapters;
    } else {
        for (let key in raw) {
            if (Array.isArray(raw[key])) {
                data = raw[key];
                break;
            }
        }
    }

    if (!data) return null;

    // ===== BUILD LIST =====
    let chapters = [];

    data.forEach(item => {
        if (item.slugId && item.name) {
            chapters.push({
                name: item.name,
                url: "https://metruyenchu.co/truyen/" + bookSlug + "/" + item.slugId
            });
        }
    });

    // 🔥 đảm bảo đúng thứ tự
    chapters.sort((a, b) => {
        let na = parseInt(a.name.match(/\d+/));
        let nb = parseInt(b.name.match(/\d+/));
        return na - nb;
    });

    return Response.success(chapters);
}