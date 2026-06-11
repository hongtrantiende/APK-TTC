function execute(key, page) {

    if (!page) page = "1";

    var host = "https://truyendocviet1.xyz";

    var url = page == "1"
        ? host + "/tim-kiem.html?q=" + encodeURIComponent(key)
        : host + "/tim-kiem/page-" + page + ".html?q=" + encodeURIComponent(key);

    console.log(url);

    var browser = Engine.newBrowser();

    browser.launch(url, 20000);

    var html = browser.html();

    browser.close();

    var doc = Html.parse(html);

    var data = [];

    var items = doc.select(".sg-product");

    for (var i = 0; i < items.size(); i++) {

        var item = items.get(i);

        var a = item.select(".product-info h3 a");

        if (!a || a.size() == 0) continue;

        var name = a.text();
        var link = host + a.attr("href");

        var cover = "";
        var img = item.select(".product-thumb img");
        if (img && img.size() > 0) {
            cover = img.attr("src");
            if (cover.indexOf("http") != 0) {
                cover = host + cover;
            }
        }

        var description = "";
        var author = item.select(".sg-rating a");
        if (author && author.size() > 0) {
            description = author.text();
        }

        data.push({
            name: name,
            link: link,
            cover: cover,
            description: description,
            host: host
        });
    }

    var next = (parseInt(page) + 1).toString();

    if (data.length == 0) return null;

    return Response.success(data, next);
}