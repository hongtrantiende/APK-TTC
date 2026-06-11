function execute(key, page) {
    if (!page) page = "1";

    // Encode keyword (rất quan trọng)
    var keyword = encodeURIComponent(key);

    var url = "https://novest.me/tim-kiem?q=" + keyword;

    // Page > 1 thì thêm &page=
    if (page != "1") {
        url += "&page=" + page;
    }

    var response = fetch(url);
    if (!response.ok) return null;

    var doc = response.html();
    var data = [];

    var items = doc.select("a.block.shrink-0.group.w-full");

    for (var i = 0; i < items.size(); i++) {
        var e = items.get(i);

        var name = e.select("h3").text();

        var rawLink = e.attr("href");
        var slug = rawLink.split("/").filter(Boolean).pop();

        // Ảnh (NextJS)
        var cover = e.select("img").attr("src");
        if (!cover) {
            cover = e.select("img").attr("data-src");
        }

        var description = e.select("p").text();

        data.push({
            name: name,
            link: slug,
            cover: cover,
            description: description,
            host: "https://novest.me"
        });
    }

    // Next page
    var next = (parseInt(page) + 1).toString();

    return Response.success(data, next);
}