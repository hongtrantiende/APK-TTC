function execute(key, page) {
    var key = encodeURIComponent(key)
    if (!page) page = '1';
    let response = fetch("https://truyendich.vn/tim-kiem?search=" + key + "&page=" + page);
    if (response) {
        let doc = response.html();
        let el = doc.select(".list .row");
        let data = [];
        let next = String(parseInt(page) + 1);
        for (var i = 0; i < el.size(); i++) {
            var e = el.get(i);
            let link = e.select(".truyen-title a").attr("href");
            let coverImg = e.select(".truyen-title img").first().attr("src");
            if (!coverImg) {
                // Fallback for search page if img is not inside title
                coverImg = e.select("img").first().attr("src");
            }
            data.push({
                name: e.select(".truyen-title").text(),
                link: link,
                description: e.select('.author').text(),
                cover: coverImg,
                host: "https://truyendich.vn/",
            });
        }
        return Response.success(data, next);
    }
    return null;
}
