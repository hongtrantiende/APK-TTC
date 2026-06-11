function execute(url) {

    var data = [];

    var base = url.replace(".html", "") + "/read/chapters.html";

    var browser = Engine.newBrowser();

    var html = browser.launch(base, 10000).html();
    var doc = Html.parse(html);

    var a = doc.select("tbody tr td:nth-child(2) a").first();

    if (!a) {
        browser.close();
        return null;
    }

    var chapterUrl = "https://truyendocviet1.xyz" + a.attr("href");

    var chapterHtml = browser.launch(chapterUrl, 10000).html();
    browser.close();

    var chapterDoc = Html.parse(chapterHtml);

    var select = chapterDoc.select("#select_chuong").first();
    var items = select.select("option");

    for (var i = 0; i < items.size(); i++) {

        var e = items.get(i);

        data.push({
            name: e.text(),
            url: "https://truyendocviet1.xyz" + e.attr("value")
        });
    }

    return Response.success(data);
}