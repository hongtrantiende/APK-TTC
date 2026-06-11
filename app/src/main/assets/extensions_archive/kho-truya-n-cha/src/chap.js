load("config.js");

function ktcCleanContent(html) {
    if (!html) return "";
    html = html.replace(/<script[\s\S]*?<\/script>/gi, "");
    html = html.replace(/<style[\s\S]*?<\/style>/gi, "");
    html = html.replace(/<noscript[\s\S]*?<\/noscript>/gi, "");
    html = html.replace(/<iframe[\s\S]*?<\/iframe>/gi, "");
    html = html.replace(/<p>\s*<\/p>/gi, "");
    return ktcTrim(html);
}

function execute(url) {
    var slug = ktcStorySlug(url);
    var chapterNum = ktcChapterId(url);
    if (!slug || !chapterNum) return Response.error("URL kh\u00F4ng h\u1EE3p l\u1EC7.");

    // Get story ID
    var storyResp = fetch(API_BASE + "/stories/" + encodeURIComponent(slug));
    if (!storyResp.ok) return Response.error("Kh\u00F4ng t\u00ECm th\u1EA5y truy\u1EC7n.");

    var storyJson = storyResp.json();
    if (!storyJson || !storyJson.data || !storyJson.data.id) {
        return Response.error("Kh\u00F4ng t\u00ECm th\u1EA5y truy\u1EC7n.");
    }

    var storyId = storyJson.data.id;
    var totalChapters = parseInt(storyJson.data.chapterCount, 10) || 0;
    var targetPage = Math.ceil(parseInt(chapterNum, 10) / 50);
    var chapterId = null;

    // Fetch the right page of chapters
    var chResp = fetch(API_BASE + "/stories/" + storyId + "/chapters?page=" + targetPage);
    if (chResp.ok) {
        var chJson = chResp.json();
        if (chJson && chJson.data && Array.isArray(chJson.data)) {
            for (var i = 0; i < chJson.data.length; i++) {
                if (String(chJson.data[i].chapterNumber) === String(chapterNum)) {
                    chapterId = chJson.data[i].id;
                    break;
                }
            }
        }
    }

    if (!chapterId) {
        return Response.error("Kh\u00F4ng t\u00ECm th\u1EA5y ch\u01B0\u01A1ng " + chapterNum + ".");
    }

    // Fetch chapter content
    var chapResp = fetch(API_BASE + "/chapters/" + chapterId);
    if (!chapResp.ok) return Response.error("Kh\u00F4ng t\u1EA3i \u0111\u01B0\u1EE3c n\u1ED9i dung.");

    var chapJson = chapResp.json();
    if (!chapJson || !chapJson.data || !chapJson.data.content) {
        return Response.error("N\u1ED9i dung ch\u01B0\u01A1ng tr\u1ED1ng.");
    }

    var content = ktcCleanContent(chapJson.data.content);
    if (!content) return Response.error("N\u1ED9i dung ch\u01B0\u01A1ng tr\u1ED1ng.");

    return Response.success(content);
}
