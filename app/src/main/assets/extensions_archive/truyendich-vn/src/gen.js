function execute(url, page) {
    if (!page) page = "1";
    // let response = fetch("https://truyendich.vn/" + url + "?page=" + page);
    let response = fetch("https://truyendich.vn" + url + "?page=" + page);
    if (response.ok) {
        let doc = response.html();
        var el = doc.select(".list .row");
        var novelList = [];
        let next = String(parseInt(page) + 1);
        for (let i = 0; i < el.size(); i++) {
            let e = el.get(i);
            let link = e.select(".truyen-title a").attr("href");
            let coverImg = e.select(".truyen-title img").first().attr("src");
            if (!coverImg) {
                coverImg = e.select("img").first().attr("src");
            }
            novelList.push({
                name: e.select(".truyen-title").text(),
                link: link,
                description: e.select('.author').text(),
                cover: coverImg,
                host: "https://truyendich.vn/",
            });
        }
        return Response.success(novelList, next);
    }
    return null;
}