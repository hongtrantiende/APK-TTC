load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    var response = fetchPage(url);
    if (!response.ok) return null;
    var html = response.text();
    var storyId = extractStoryId(html);
    if (!storyId) return null;
    var apiBase = 'https://api.ntruyen.biz/novels/' + storyId + '/chapters?page=';
    var apiResponse = fetchPage(apiBase + '1');
    if (!apiResponse.ok) return null;
    var json = JSON.parse(apiResponse.text());
    var totalPages = json.totalPages || 1;
    var data = [];
    for (var i = 1; i <= totalPages; i++) {
        data.push(apiBase + i);
    }
    return Response.success(data);
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
