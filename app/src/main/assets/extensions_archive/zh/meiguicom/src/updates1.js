load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".border3-1.popular .list-out").forEach(e => {
            let genre = e.select("span").first().text().replace("[", "").replace("]", "");
            let novelLink = BASE_URL + e.select("a").attr("href");

            // Fetch details page to get cover image
            let detailResponse = fetch(novelLink);
            if (detailResponse.ok) {
                let detailDoc = detailResponse.html();
                let coverElement = detailDoc.select(".info-main img").first();
                let cover = coverElement != null ? coverElement.attr("data-original") : null;

                // Check if the cover link is relative and convert it to absolute
                cover = cover != null && cover.startsWith("http") ? cover : BASE_URL + cover;

                let novelTitle = e.select("a").text();
                let author = e.select(".gray").text();

                let description = `${genre} - ${author}`;

                data.push({
                    name: novelTitle,
                    link: novelLink,
                    description: description,
                    cover: cover,  // Include the cover in the result
                    host: BASE_URL
                });
            }
        });

        return Response.success(data);
    }
    return null;
}