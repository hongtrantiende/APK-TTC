load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let data = [];
        appendChapters(data, doc);

        let html = doc.html();
        let storyIdMatch = html.match(/page\((\d+),\s*\d+\)/);
        let totalPages = 1;

        let pageMatches = html.match(/page\(\d+,\s*(\d+)\)/g);
        if (pageMatches) {
            for (let i = 0; i < pageMatches.length; i++) {
                let match = pageMatches[i].match(/page\(\d+,\s*(\d+)\)/);
                if (match && parseInt(match[1]) > totalPages) totalPages = parseInt(match[1]);
            }
        }

        if (storyIdMatch) {
            let storyId = storyIdMatch[1];
            for (let page = 2; page <= totalPages; page++) {
                let apiResponse = fetch(BASE_URL + "/get/listchap/" + storyId + "?page=" + page);
                if (!apiResponse.ok) break;

                let json = apiResponse.json();
                if (!json || !json.data) break;

                let pageDoc = Html.parse(json.data);
                let before = data.length;
                appendChapters(data, pageDoc);
                if (data.length === before) break;
            }
        }

        return Response.success(data);
    }

    return null;
}

function appendChapters(data, doc) {
    doc.select("#chapter-list li a, li a[href*='/chuong']").forEach(function (e) {
        let name = cleanText(e.text());
        let href = e.attr("href");
        if (name && href) {
            data.push({
                name: name,
                url: normalizeUrl(href),
                host: BASE_URL
            });
        }
    });
}
