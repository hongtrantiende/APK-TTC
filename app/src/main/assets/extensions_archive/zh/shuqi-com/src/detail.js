load("config.js");

function ensureAbsolute(u) {
    if (!u) return "";
    return u.startsWith("http") ? u : BASE_URL + (u.startsWith("/") ? "" : "/") + u;
}

function ensureSpm(u) {
    if (!u) return "";
    const SPM = "aliwx.pc-web-bookstore.0.0";
    // giữ nguyên phần hash nếu có
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

function absolutize(url) {
    if (!url) return "";
    if (url.startsWith("//")) return "https:" + url;
    if (!url.startsWith("http")) return BASE_URL + (url.startsWith("/") ? "" : "/") + url;
    return url;
}

function execute(url) {
    // Luôn thêm spm trước khi fetch để tránh 404
    const finalUrl = ensureSpm(ensureAbsolute(url));

    let response = fetch(finalUrl);
    if (!response || !response.ok) {
        // thử lại một lần nữa với spm đã ép
        const retryUrl = ensureSpm(finalUrl);
        response = fetch(retryUrl);
        if (!response || !response.ok) return null;
    }

    const doc = response.html();

    // Title, author, cover, description
    let bookTitle = (doc.select(".infoarea .view .bname, .bname").text() || "").trim();
    let author = (doc.select(".infoarea .bauthor a, .bauthor a, .author a, .author").text() || "").trim();
    let cover = (
        doc.select(".infoarea .view img.cover").attr("data-src") ||
        doc.select(".infoarea .view img.cover").attr("data-original") ||
        doc.select(".infoarea .view img.cover").attr("src") ||
        ""
    ).trim();
    let description = (doc.select(".infoarea .bookDesc, .bookDesc").text() || "").trim();

    // Make cover absolute
    cover = absolutize(cover);

    // Meta info
    const lastLis = doc.select(".infoarea .lastchapter li");
    const getLi = (i) => (lastLis.size && lastLis.size() > i ? (lastLis.get(i).text() || "").trim() : "");
    let category = getLi(0);
    let wordCount = getLi(1);
    let status = getLi(2);
    let updateTime = getLi(3);

    // Tags (genres)
    let genres = [];
    doc.select(".infoarea .tags li a, .tags a").forEach(a => {
        let title = (a.text() || "").trim();
        let href = absolutize((a.attr("href") || "").trim());
        if (!title) return;
        genres.push({
            title: title,
            input: href || "",
            script: "source.js"
        });
    });
    if (genres.length === 0 && category) {
        genres.push({
            title: category,
            input: "",
            script: "source.js"
        });
    }

    // Detail block
    let detail = `
        <div>
            <p><strong>分类:</strong> ${category}</p>
            <p><strong>状态:</strong> ${status}</p>
            <p><strong>字数:</strong> ${wordCount}</p>
            <p><strong>更新时间:</strong> ${updateTime}</p>
        </div>
    `;

    return Response.success({
        name: bookTitle,
        cover: cover,
        author: author,
        description: description,
        genres: genres,
        detail: detail,
        ongoing: status ? (status.indexOf("连载") !== -1 || status.indexOf("完") === -1) : true,
        host: BASE_URL,
    });
}   