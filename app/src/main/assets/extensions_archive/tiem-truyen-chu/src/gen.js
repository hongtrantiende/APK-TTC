load("config.js");

function parseApiUrl(input) {
    if (!input || input.indexOf("api://") !== 0) return null;
    let pathAndQuery = input.substring(6);
    let parts = pathAndQuery.split("?");
    let path = parts[0];
    let query = parts.length > 1 ? parts[1] : "";
    return { path: path, query: query };
}

function buildApiUrl(path, query, page) {
    let url = API_BASE + "/api/v1/" + path + "?";
    if (query) url += query + "&";
    url += "page=" + page + "&limit=30";
    return url;
}

function parseStoriesFromJson(json) {
    let list = [];
    let items = json.items || [];
    items.forEach(function(item) {
        let cover = item.cover_url || item.poster || "";
        if (cover && cover.indexOf("http") !== 0) cover = API_BASE + cover;

        let link = API_BASE + "/truyen/" + item.id;

        let desc = "";
        if (item.author) desc = item.author;
        if (item.latest_chapter_title) desc += (desc ? " | " : "") + item.latest_chapter_title;
        if (item.category) desc += (desc ? " | " : "") + item.category;

        list.push({
            name: item.title || "",
            link: link,
            cover: cover,
            description: desc,
            host: API_BASE
        });
    });
    return list;
}

function parseRankingFromJson(json) {
    let list = [];
    let items = json.items || [];
    items.forEach(function(item) {
        let cover = item.cover_url || item.poster || "";
        if (cover && cover.indexOf("http") !== 0) cover = API_BASE + cover;

        let link = API_BASE + "/truyen/" + item.id;

        let desc = "#" + item.rank;
        if (item.author) desc += " | " + item.author;
        if (item.category) desc += " | " + item.category;

        list.push({
            name: item.title || "",
            link: link,
            cover: cover,
            description: desc,
            host: API_BASE
        });
    });
    return list;
}

function parseNextFromJson(json, page) {
    let currentPage = parseInt(json.page || page || "1", 10);
    let totalPages = parseInt(json.totalPages || json.total_pages || "0", 10);
    if (currentPage < totalPages) return String(currentPage + 1);
    return "";
}

function execute(url, page) {
    if (!page) page = "1";

    let parsed = parseApiUrl(url);
    if (parsed) {
        let apiUrl = buildApiUrl(parsed.path, parsed.query, page);
        let response = ttcFetchJson(apiUrl);
        if (response && response.ok) {
            try {
                let json = ttcResponseJson(response);
                if (json && json.success && json.items) {
                    let list;
                    if (parsed.path === "ranking") {
                        list = parseRankingFromJson(json);
                    } else {
                        list = parseStoriesFromJson(json);
                    }
                    let next = parseNextFromJson(json, page);
                    return Response.success(list, next);
                }
            } catch (error) {}
        }
        return Response.success([]);
    }

    // Fallback: treat as regular URL (for backward compatibility)
    let normalized = ttcNormalizeUrl(url);
    if (normalized.indexOf("?") === -1) {
        normalized += "?page=" + page;
    } else if (normalized.indexOf("page=") === -1) {
        normalized += "&page=" + page;
    } else {
        normalized = normalized.replace(/page=\d+/, "page=" + page);
    }

    // Try JSON API first
    let jsonUrl = normalized;
    if (jsonUrl.indexOf("?") === -1) {
        jsonUrl += "?ajax=1";
    } else if (jsonUrl.indexOf("ajax") === -1) {
        jsonUrl += "&ajax=1";
    }

    let response = ttcFetchJson(jsonUrl);
    if (response && response.ok) {
        try {
            let json = ttcResponseJson(response);
            if (json && (json.success || json.stories || json.data)) {
                let list = parseStoriesFromJson(json);
                if (list.length > 0) {
                    let next = parseNextFromJson(json, page);
                    return Response.success(list, next);
                }
            }
        } catch (error) {}
    }

    // Fall back to HTML parsing
    let pageData = ttcFetchPage(normalized, null, 12000);
    if (!pageData || !pageData.doc || pageData.loginRequired) {
        return Response.success([]);
    }

    // Parse HTML story list
    let list = [];
    let items = pageData.doc.select("#story-list-container .story-item");
    if (items.size() === 0) items = pageData.doc.select(".story-item");

    items.forEach(function(item) {
        let titleEl = item.select("a.story-title").first();
        if (!titleEl) titleEl = item.select("a[href*='/truyen/']").first();
        if (!titleEl) titleEl = item.select("a").first();
        if (!titleEl) return;

        let link = ttcToAbsolute(titleEl.attr("href"));
        let name = ttcTrim(titleEl.text().replace(/\s+/g, " "));
        let coverEl = item.select("img.story-poster").first();
        if (!coverEl) coverEl = item.select("img").first();
        let cover = coverEl ? (coverEl.attr("data-src") || coverEl.attr("src") || "") : "";

        if (!name || !link) return;
        list.push({
            name: name,
            link: link,
            cover: ttcToAbsolute(cover),
            description: "",
            host: BASE_URL
        });
    });

    // Parse next page from HTML
    let next = "";
    let current = parseInt(page || "1", 10);
    pageData.doc.select("a[href*='page=']").forEach(function(item) {
        let href = item.attr("href") || "";
        let matched = href.match(/[?&]page=(\d+)/);
        if (!matched) return;
        let value = parseInt(matched[1], 10);
        if (value > current && (!next || value < parseInt(next, 10))) {
            next = String(value);
        }
    });

    return Response.success(list, next);
}