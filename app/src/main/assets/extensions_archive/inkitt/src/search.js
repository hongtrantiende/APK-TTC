load('config.js');

function execute(key, page) {
    if (!page) page = "1";

    let apiUrl = "https://www.inkitt.com/api/2/search/top?q=" 
        + encodeURIComponent(key) 
        + "&page=" + page;

    let res = fetch(apiUrl, {
        headers: {
            "accept": "application/json",
            "content-type": "application/json",
            "referer": "https://www.inkitt.com/search?q=" + key
        }
    });

    if (!res.ok) return null;

    let json = res.json();
    let books = [];

    try {
        // tuỳ response nhưng thường nằm ở đây
        let items = json.stories || json.results || json.data || [];

        for (let i = 0; i < items.length; i++) {
            let item = items[i];

            let id = item.id;

            let name = item.title || item.name || "";
            let link = BASE_URL + "/stories/" + id;
            let cover = item.cover.url || item.cover_image || item.image || "";
            let description = item.teaser || item.summary || "";

            books.push({
                name: name,
                link: link,
                cover: cover,
                description: description,
                host: "https://www.inkitt.com"
            });
        }

    } catch (e) {
        console.log("Parse error: " + e);
    }

    let next = parseInt(page) + 1;
    return Response.success(books, next.toString());
}