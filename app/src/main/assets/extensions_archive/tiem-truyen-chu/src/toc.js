load("config.js");

function extractStoryId(url) {
    let matched = (url || "").match(/\/truyen\/(\d+)/i);
    return matched ? parseInt(matched[1], 10) : 0;
}

function buildSyntheticToc(storyId, totalChapters) {
    let list = [];
    for (let i = 1; i <= totalChapters; i++) {
        list.push({
            name: "Chuong " + i,
            url: "api://chapter-content?story_id=" + storyId + "&chapter_number=" + i,
            host: API_BASE
        });
    }
    return list;
}

function execute(url) {
    let normalized = ttcNormalizeUrl(url);
    let storyId = extractStoryId(normalized);

    if (storyId) {
        // Try API first (requires auth)
        let apiUrl = API_BASE + "/api/v1/stories/" + storyId + "/chapters?page=1&limit=500";
        let response = ttcFetch(apiUrl);
        if (response && response.ok) {
            try {
                let json = ttcResponseJson(response);
                if (json && json.success && json.items) {
                    let list = [];
                    json.items.forEach(function(item) {
                        let chapNum = item.chapter_number || item.chapterNumber || item.number || 0;
                        let chapTitle = item.title || "Chuong " + chapNum;
                        let chapId = item.id || chapNum;
                        list.push({
                            name: chapTitle,
                            url: "api://chapter-content?story_id=" + storyId + "&chapter_id=" + chapId + "&chapter_number=" + chapNum,
                            host: API_BASE
                        });
                    });
                    if (list.length > 0) return Response.success(list);
                }
            } catch (error) {}
        }

        // Try web page
        let page = ttcFetchPage(normalized, null, 15000);
        if (page && page.doc && !page.loginRequired) {
            let list = [];
            let items = page.doc.select("#chapter-list-container a.chapter-item-link");
            if (items.size() === 0) items = page.doc.select("a.chapter-item-link");
            if (items.size() === 0) items = page.doc.select(".chapter-list a");

            items.forEach(function(item) {
                let name = ttcTrim(item.text());
                let link = ttcToAbsolute(item.attr("href"));
                if (!name || !link) return;
                list.push({
                    name: name,
                    url: link,
                    host: BASE_URL
                });
            });

            if (list.length > 0) return Response.success(list);
        }

        // Fallback: synthetic TOC from story detail API
        let detailUrl = API_BASE + "/api/v1/stories/" + storyId;
        let detailResp = ttcFetchJson(detailUrl);
        if (detailResp && detailResp.ok) {
            try {
                let detailJson = ttcResponseJson(detailResp);
                if (detailJson && detailJson.success && detailJson.story) {
                    let totalChapters = parseInt(detailJson.story.total_chapters || "0", 10);
                    if (totalChapters > 0) {
                        return Response.success(buildSyntheticToc(storyId, totalChapters));
                    }
                }
            } catch (error) {}
        }
    }

    return Response.success([]);
}