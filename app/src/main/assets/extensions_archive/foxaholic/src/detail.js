load('config.js');

function rowLabel(row) {
    let node = row.select(".summary-heading h5, .summary-heading, .label, b, strong").first();
    return node ? cleanText(node.text()).replace(/:$/, "") : "";
}

function rowValue(row) {
    let node = row.select(".summary-content, .summary-content_wrap").first();
    return node ? cleanText(node.text()) : cleanText(row.text().replace(rowLabel(row), ""));
}

function valueByLabels(doc, labels) {
    let rows = doc.select(".post-content_item, .summary-content_wrap .post-content_item");
    for (let i = 0; i < rows.size(); i++) {
        let row = rows.get(i);
        let label = foldText(rowLabel(row));
        for (let j = 0; j < labels.length; j++) {
            if (label.indexOf(labels[j]) >= 0) return rowValue(row);
        }
    }
    return "";
}

function buildDetail(doc) {
    let lines = [];
    let seen = {};
    doc.select(".post-content_item").forEach(function(row) {
        let label = rowLabel(row);
        let value = rowValue(row);
        if (!label || !value || seen[label]) return;
        seen[label] = true;
        lines.push("<b>" + label + ":</b> " + value);
    });
    return lines.join("<br>");
}

function getGenres(doc) {
    let data = [];
    let seen = {};
    doc.select(".genres-content a[href], .post-content_item a[href*='/genre/'], a[href*='/genre/']").forEach(function(a) {
        let href = normalizeUrl(a.attr("href"));
        let title = cleanText(a.text());
        if (!title || !href || seen[href]) return;
        seen[href] = true;
        data.push({title: title, input: href, script: "gen.js"});
    });
    return data;
}

function latestChapters(doc) {
    let parts = [];
    let seen = {};
    doc.select(".wp-manga-chapter a[href], .chapter-list a[href], a[href*='/chapter-']").forEach(function(a) {
        let href = a.attr("href");
        let text = cleanText(a.text());
        if (!text || !isChapterUrl(href) || seen[href]) return;
        seen[href] = true;
        if (parts.length < 5) parts.push(text);
    });
    return parts.join("<br>");
}

function execute(url) {
    let target = storyUrlFromAny(url);
    let doc = getDoc(target);
    if (!doc) return loadError();

    let name = firstText(doc, [".post-title h1", ".profile-manga .post-title h1", "h1"]);
    if (!name) name = metaContent(doc, "meta[property=og:title], meta[name=twitter:title]").replace(/\s+-\s+Foxaholic$/i, "");
    if (!name) return Response.error("Khong tim thay thong tin truyen.");

    let cover = metaImage(doc);
    if (!cover) cover = imageFromNode(doc.select(".summary_image img, .profile-manga img, .tab-summary img, .item-thumb img").first());

    let author = valueByLabels(doc, ["author", "tac gia"]);
    if (!author) author = cleanText(doc.select(".author-content a").text());
    let status = valueByLabels(doc, ["status", "translation", "novel"]);
    let statusFold = foldText(status);
    let description = firstHtml(doc, [".description-summary .summary__content", ".description-summary", ".summary__content", ".entry-content .summary__content"]);
    if (!description) description = metaContent(doc, "meta[name=description], meta[property=og:description]");

    let detail = buildDetail(doc);
    let latest = latestChapters(doc);
    if (latest) detail += (detail ? "<br>" : "") + "<b>Latest:</b><br>" + latest;

    return Response.success({
        name: name,
        cover: cover,
        host: BASE_URL,
        author: author,
        description: description,
        detail: detail,
        ongoing: statusFold.indexOf("completed") < 0 && statusFold.indexOf("complete") < 0 && statusFold.indexOf("ended") < 0,
        genres: getGenres(doc)
    });
}
