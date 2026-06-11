function execute(url) {
    if (url.slice(-1) !== "/") url = url + "/";
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        var genres = [];
        doc.select('a[itemprop="genre"]').forEach(e => {
            genres.push({
                title: e.text(),
                input: e.attr("href").replace("https://truyendich.vn", ""),
                script: "gen.js"
            });
        });
        let coverImg = doc.select(".book img").first().attr("src");
        let author = doc.select('.info a[href*="/tac-gia/"]').first().text();
        let ongoing = doc.select(".info").text().indexOf("Đang ra") >= 0;

        return Response.success({
            name: doc.select('h3.title').first().text(),
            cover: coverImg,
            author: author,
            description: doc.select(".desc-text").text(),
            host: "https://truyendich.vn/",
            genres: genres,
            ongoing: ongoing
        });
    }

    return null;
}