function execute(url) {
    
    var fetchUrl = url.replace(".html", "/read/chapters.html");

    var browser = Engine.newBrowser();
    var response = browser.launch(fetchUrl, 5000).html(); // Hàm này trả về String HTML
    browser.close();

    var doc = Html.parse(response);

    // Tên truyện
    var name = doc.select("h1 a").text();

    // Tác giả
    var author = doc.select("a[data-author-id]").text();

    // Cover
    var cover = doc.select("meta[property=og:image]").attr("content");

    // Thể loại
    var genres = [];
    var genreEls = doc.select(".mb-3 a span.badge");

    for (var i = 0; i < genreEls.size(); i++) {
        genres.push(genreEls.get(i).text().replace(",", "").trim());
    }

    // Số chương
    var chapterCount = doc.select(".mb-3:contains(Số chương)").last().text();

    // Cập nhật
    var update = doc.select(".mb-3:contains(Cập nhật) span").text();

    //Giới thiệu
    var description = doc.select("p.tom_tat_truyen").text();

    return Response.success({
        name: name,
        author: author,
        cover: cover,
        description: description,
        detail: "Cập nhật: " + update + "\n" + "Thể loại: " + genres.join(", ") + "\n" + chapterCount,
        host: "https://truyendocviet1.xyz"
    });
}