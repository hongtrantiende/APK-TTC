load('config.js');

function execute(url) {
    url = normalizeUrl(url);
    let response = fetch(url);
    if (!response.ok) return null;

    let doc = response.html();
    let html = doc.html();

    let mangaIdMatch = html.match(/data-post=["'](\d+)["']/);
    if (!mangaIdMatch) {
        mangaIdMatch = html.match(/manga_id["']?\s*[:=]\s*["']?(\d+)/);
    }
    if (!mangaIdMatch) return null;

    let mangaId = mangaIdMatch[1];
    let data = [];
    let seen = {};

    let storySlug = url.replace(BASE_URL + "/truyen/", "").replace(/\/$/, "");

    let ranges = [
        {from: 1, to: 100}, {from: 101, to: 200},
        {from: 201, to: 300}, {from: 301, to: 400},
        {from: 401, to: 500}, {from: 501, to: 600},
        {from: 601, to: 700}, {from: 701, to: 800},
        {from: 801, to: 900}, {from: 901, to: 1000},
        {from: 1001, to: 1100}, {from: 1101, to: 1200},
        {from: 1201, to: 1300}, {from: 1301, to: 1400},
        {from: 1401, to: 1500}, {from: 1501, to: 1600},
        {from: 1601, to: 1700}, {from: 1701, to: 1800},
        {from: 1801, to: 1900}, {from: 1901, to: 2000}
    ];

    for (let i = 0; i < ranges.length; i++) {
        let range = ranges[i];
        let apiResponse = fetch(BASE_URL + "/api/api-chapters.php", {
            method: "POST",
            headers: {
                "X-Custom-Auth": "abC0000011111",
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: {
                manga_id: mangaId,
                from: range.from,
                to: range.to,
                vol: ""
            }
        });

        if (!apiResponse.ok) break;

        let json = apiResponse.json();
        if (!json || !Array.isArray(json) || json.length === 0) break;

        let before = data.length;
        for (let j = 0; j < json.length; j++) {
            let ch = json[j];
            let slug = ch.s || ch["0"];
            let name = ch.n || ch["1"];
            let extra = ch.e || ch["2"] || "";
            if (extra) name = name + " - " + extra.trim();

            if (slug && name && !seen[slug]) {
                seen[slug] = true;
                data.push({
                    name: cleanText(name),
                    url: BASE_URL + "/truyen/" + storySlug + "/" + slug + "/",
                    host: BASE_URL
                });
            }
        }

        if (data.length === before) break;
        if (json.length < 100) break;
    }

    return Response.success(data);
}
