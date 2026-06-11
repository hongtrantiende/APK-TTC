load("config.js");

function extractStoryId(url) {
    let matched = (url || "").match(/\/truyen\/(\d+)/i);
    return matched ? parseInt(matched[1], 10) : 0;
}

function parseStoryDetailFromJson(json) {
    let story = json.story || json;
    if (!story) return null;

    let name = ttcTrim(story.title || "");
    if (!name) return null;

    let cover = story.cover_url || story.poster || "";
    if (cover && cover.indexOf("http") !== 0) cover = API_BASE + cover;

    let author = ttcTrim(story.author || "");

    let genres = [];
    let genreNames = [];

    if (story.category) {
        genreNames.push(story.category);
        genres.push({
            title: story.category,
            input: "api://stories?cat=" + encodeURIComponent(sttcSlugify(story.category)),
            script: "gen.js"
        });
    }

    if (story.sub_categories && Array.isArray(story.sub_categories)) {
        story.sub_categories.forEach(function(sub) {
            let title = ttcTrim(sub || "");
            if (!title) return;
            genreNames.push(title);
            genres.push({
                title: title,
                input: "api://stories?keyword=" + encodeURIComponent(title),
                script: "gen.js"
            });
        });
    }

    let status = "Dang ra";
    if (story.status) {
        let raw = ttcFoldText(story.status);
        if (raw.indexOf("full") > -1 || raw.indexOf("hoan") > -1) status = "Hoan thanh";
    }

    let detail = "";
    if (author) detail += "Tac gia: " + author + "<br>";
    if (genreNames.length > 0) detail += "The loai: " + genreNames.join(", ") + "<br>";
    detail += "Trang thai: " + status + "<br>";
    if (story.total_chapters) detail += "So chuong: " + story.total_chapters + "<br>";
    if (story.views) detail += "Luot xem: " + story.views + "<br>";
    if (story.follows !== null && story.follows !== undefined) detail += "Theo doi: " + story.follows;

    let description = ttcTrim(story.description || "");
    if (description) {
        description = description.replace(/\r\n/g, "<br>").replace(/\n/g, "<br>");
    }
    if (story.latest_chapter_title) {
        if (description) description += "<br><br>";
        description += "<b>Chuong moi nhat:</b> " + story.latest_chapter_title;
    }

    return {
        name: name,
        cover: cover,
        author: author,
        description: description,
        detail: detail,
        host: API_BASE,
        genres: genres,
        ongoing: ttcFoldText(status).indexOf("hoan") === -1
    };
}

function sttcSlugify(text) {
    let value = ttcTrim(text).toLowerCase();
    try {
        if (typeof value.normalize === "function") {
            value = value.normalize("NFD").replace(/[\u0300-\u036f]/g, "");
        }
    } catch (e) {}
    return value.replace(/\u0111/g, "d").replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "");
}

function parseFromDoc(doc) {
    let name = ttcTrim(doc.select(".story-header h2 span.align-middle").last().text());
    if (!name) {
        let titleEl = doc.select("h1.story-title").first();
        if (!titleEl) titleEl = doc.select("h1").first();
        if (!titleEl) titleEl = doc.select("h2").first();
        if (titleEl) name = ttcTrim(titleEl.text());
    }
    if (!name) return null;

    let coverEl = doc.select("img.story-poster").first();
    if (!coverEl) coverEl = doc.select(".story-poster").first();
    if (!coverEl) coverEl = doc.select("meta[property=og:image]").first();
    let cover = "";
    if (coverEl) {
        cover = coverEl.attr("data-src") || coverEl.attr("src") || coverEl.attr("content") || "";
    }

    let author = "";
    let authorEl = doc.select("a.tag-pill[href*='/tac-gia/']").first();
    if (authorEl) author = ttcTrim(authorEl.text());

    let genres = [];
    let genreNames = [];
    doc.select("a.tag-pill[href*='cat=']").forEach(function(item) {
        let title = ttcTrim(item.text());
        let href = item.attr("href") || "";
        if (!title || !href) return;
        genres.push({ title: title, input: href, script: "gen.js" });
        genreNames.push(title);
    });

    let status = "Dang ra";
    let detail = "";
    if (author) detail += "Tac gia: " + author + "<br>";
    if (genreNames.length > 0) detail += "The loai: " + genreNames.join(", ") + "<br>";
    detail += "Trang thai: " + status;

    let description = "";
    let descriptionEl = doc.select("#tab-info").first();
    if (descriptionEl) description = descriptionEl.html();
    if (!description) description = doc.select(".content-text").html();
    if (!description) description = ttcTrim(doc.select("meta[name=description]").attr("content"));

    return {
        name: name,
        cover: ttcToAbsolute(cover),
        author: author,
        description: description,
        detail: detail,
        host: BASE_URL,
        genres: genres,
        ongoing: true
    };
}

function execute(url) {
    let normalized = ttcNormalizeUrl(url);
    let storyId = extractStoryId(normalized);

    if (storyId) {
        let apiUrl = API_BASE + "/api/v1/stories/" + storyId;
        let response = ttcFetchJson(apiUrl);
        if (response && response.ok) {
            try {
                let json = ttcResponseJson(response);
                if (json && json.success) {
                    let parsed = parseStoryDetailFromJson(json);
                    if (parsed && parsed.name) {
                        return Response.success(parsed);
                    }
                }
            } catch (error) {}
        }
    }

    // Fallback: try web page
    let page = ttcFetchPage(normalized, null, 15000);
    if (page && page.doc && !page.loginRequired) {
        let parsed = parseFromDoc(page.doc);
        if (parsed && parsed.name) {
            return Response.success(parsed);
        }
    }

    if (ttcHasCookie()) {
        return Response.error("Khong tai duoc chi tiet truyen. Cookie dang nhap co the da het han.");
    }
    return Response.error("Khong tai duoc chi tiet truyen.");
}