load("config.js");

function extractContent(doc) {
    let contentEl = doc.select(".chapter-content").first();
    if (!contentEl) contentEl = doc.select("#chapter-content").first();
    if (!contentEl) contentEl = doc.select(".content-text").first();
    if (!contentEl) contentEl = doc.select("#content-text").first();
    if (!contentEl) contentEl = doc.select(".chapter-body").first();
    if (!contentEl) contentEl = doc.select(".story-content").first();
    if (!contentEl) return "";

    contentEl.select("script").remove();
    contentEl.select("style").remove();
    contentEl.select("noscript").remove();
    contentEl.select(".ads-container").remove();
    contentEl.select(".ad-content").remove();

    let content = contentEl.html() || "";
    content = content.replace(/\\n/g, "<br/>");
    content = content.replace(/<br\s*\/?>\s*<br\s*\/?>/gi, "<br/>");
    return ttcTrim(content);
}

function execute(url) {
    // Handle API-style chapter content URL
    if (url && url.indexOf("api://chapter-content") === 0) {
        let query = url.substring(url.indexOf("?") + 1);
        let params = {};
        query.split("&").forEach(function(pair) {
            let parts = pair.split("=");
            if (parts.length === 2) params[parts[0]] = decodeURIComponent(parts[1]);
        });

        let storyId = params.story_id || "";
        let chapterId = params.chapter_id || "";
        let chapterNumber = params.chapter_number || "";

        if (storyId && chapterNumber) {
            // Try API chapter content endpoint
            let apiUrl = API_BASE + "/api/v1/chapters/content?story_id=" + storyId + "&chapter_number=" + chapterNumber;
            let response = ttcFetch(apiUrl);
            if (response && response.ok) {
                try {
                    let json = ttcResponseJson(response);
                    if (json && json.success) {
                        let content = json.content || json.body || json.html || json.text || "";
                        if (content) return Response.success(content);
                    }
                } catch (error) {}
            }
        }

        // Fallback: try web page
        let webUrl = API_BASE + "/doc-truyen/" + storyId + "/chuong/" + chapterNumber;
        let page = ttcFetchPage(webUrl, null, 15000);
        if (page && page.doc) {
            if (page.loginRequired) {
                if (ttcHasCookie()) {
                    return Response.error("Khong doc duoc chuong. Cookie dang nhap co the da het han.");
                }
                return Response.error("Chuong yeu cau dang nhap.");
            }
            let content = extractContent(page.doc);
            if (content) return Response.success(content);
        }

        return Response.error("Khong the tai noi dung chuong.");
    }

    // Handle regular web URL
    let page = ttcFetchPage(url, null, 15000);
    if (!page || !page.doc) {
        return Response.error("Khong the tai noi dung chuong.");
    }

    if (page.loginRequired) {
        if (ttcHasCookie()) {
            return Response.error("Khong doc duoc chuong. Cookie dang nhap co the da het han.");
        }
        return Response.error("Chuong yeu cau dang nhap.");
    }

    let content = extractContent(page.doc);
    if (!content) {
        return Response.error("Khong tim thay noi dung chuong.");
    }

    return Response.success(content);
}