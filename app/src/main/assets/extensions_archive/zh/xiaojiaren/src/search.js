load("config.js");
function execute(key, page) {
    if(!page) page = 1
    let response = fetch(BASE_URL + "/search/?searchkey=" + key);
    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select(".partlist-info dl").forEach(e => {
           let coverImg = doc.select('meta[property="og:image"]').attr("content");
           let descriptionMeta = doc.select('meta[property="og:description"]').attr("content");
           let novelTitle = doc.select('meta[property="og:novel:book_name"]').attr("content");
           let newChap = doc.select(".laybox ul.list li a").first().text();
           let author = doc.select('meta[property="og:novel:author"]').attr("content");
           let novelCategory = doc.select('meta[property="og:novel:category"]').attr("content");
           let status = doc.select('meta[property="og:novel:status"]').attr("content");
           let updateTime = doc.select('meta[property="og:novel:update_time"]').attr("content").replace(/\d\d:\d\d:\d\d/g, "");
            books.push({
              name: novelTitle,
              link: e.select("a").first().attr("href"),
              cover: coverImg,
              description: descriptionMeta,
              host: BASE_URL
            })
        });
        let next = (parseInt(page) + 1).toString();
        return Response.success(books,next);
    }

    return null;
}