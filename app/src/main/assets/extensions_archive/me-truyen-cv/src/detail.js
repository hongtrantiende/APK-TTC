load("config.js");

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url);

    if (response.ok) {
        let doc = response.html();
        let genres = [];
        doc.select(`.genres-content a`).forEach(e => {
            genres.push({
                title: e.text(),
                input: e.attr("href"),
                script: "book.js"
            });
        });

        return Response.success({
            name: doc.select(".post-title h1").text(),
            cover: doc.select(".summary_image img").attr("src"),
            host: BASE_URL,
            author: doc.select(".author-content a").text(),
            description: doc.select(".summary__content.show-more p").text(),
            ongoing: doc.select(".artist-content").text().includes("Hoàn thành") ? false : true,
            genres: genres,
        });
    }
    return null;
}