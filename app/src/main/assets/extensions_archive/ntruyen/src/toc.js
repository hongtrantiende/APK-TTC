load('config.js');

function execute(url) {
    var apiUrl = buildApiUrl(url, BASE_URL);
    if (!apiUrl) return null;
    var listchapter = [];
    var response = fetchPage(apiUrl);

    if (!response.ok) {
        // Return empty if fetch fails, don't block other pages
        return Response.success(listchapter);
    }

    var json;
    try {
        json = JSON.parse(response.text());
    } catch (e) {
        // Return empty if parse fails
        return Response.success(listchapter);
    }

    var chapters = json.chapters || [];

    chapters.forEach(function(e) {
        listchapter.push({
            name: e.name,
            url: "/doc-truyen/" + e.slug + "-" + e.id,
            host: BASE_URL
        });
    });

    return Response.success(listchapter);
}

function buildApiUrl(url, baseUrl) {
    if (!url) return null;
    // If already API URL, return as-is (preserve page number)
    if (url.indexOf('api.ntruyen.biz/novels/') >= 0) return url;
    var normalized = url;
    if (normalized.indexOf('http') !== 0) {
        normalized = baseUrl + normalized;
    }
    normalized = normalized.replace(
        /^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img,
        BASE_URL
    );
    var response = fetchPage(normalized);
    if (!response.ok) return null;
    var html = response.text();
    var storyId = extractStoryId(html);
    if (!storyId) return null;
    // Extract page number from URL if present
    var pageMatch = url.match(/[?&]page=(\d+)/);
    var pageNum = pageMatch ? pageMatch[1] : '1';
    return 'https://api.ntruyen.biz/novels/' + storyId + '/chapters?page=' + pageNum;
}

function extractStoryId(html) {
    var normalized = html.replace(/\\"/g, '"');
    var patterns = [
        /"data"\s*:\s*\{"id"\s*:\s*(\d+)/,
        /"novelId"\s*:\s*(\d+)/
    ];
    for (var i = 0; i < patterns.length; i++) {
        var match = normalized.match(patterns[i]);
        if (match) return match[1];
    }
    return null;
}