function execute(url, page) {
    if (!page) page = "1";
    const BASE_URL = "https://truyendocviet1.xyz/";

    var fetchUrl = page == "1" ? url : url.replace(".html", "") + "/page-" + page + ".html";
    console.log(fetchUrl);

    var browser = Engine.newBrowser();
    var response = browser.launch(fetchUrl, 5000).html(); // Hàm này trả về String HTML
    browser.close();
    if (response !== null) {

        let doc = Html.parse(response);;

        var data = [];

        var items = doc.select(".sg-product");

        for (var i = 0; i < items.size(); i++) {

            var e = items.get(i);

            var rawLink = e.select(".product-thumb a").attr("href");

            var slug = rawLink.split("/").filter(Boolean).pop().replace(".html","");

            data.push({
                name: e.select("h3 a").text(),
                link: BASE_URL + "doc-truyen/" + slug + ".html",
                cover: e.select(".product-thumb img").attr("src"),
                description: e.select(".badge.bg-info").text(),
                host: BASE_URL
            });
        }

        var next = (parseInt(page) + 1).toString();

        return Response.success(data, next);
    }

    return null;
}