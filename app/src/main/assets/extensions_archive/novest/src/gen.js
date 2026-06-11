function execute(url, page) {
    if (!page) page = "1";

    var fetchUrl = url;

    // Page 1 không có ?page
    if (page != "1") {
        if (url.indexOf("?") > -1) {
            fetchUrl = url + "&page=" + page;
        } else {
            fetchUrl = url + "?page=" + page;
        }
    }

    var response = fetch(fetchUrl);
    if (!response.ok) return null;

    var doc = response.html();
    var data = [];

    var items = doc.select("a.block.shrink-0.group.w-full");

    for (var i = 0; i < items.size(); i++) {
        var e = items.get(i);

        var name = e.select("h3").text();

        var rawLink = e.attr("href");
        var slug = rawLink.split("/").filter(Boolean).pop();

        // Lấy ảnh (fix lazy load NextJS)
        var cover = e.select("img").attr("src");
        if (!cover) {
            cover = e.select("img").attr("data-src");
        }

        // Lấy description
        var description = e.select("p").text();

        data.push({
            name: name,
            link: "https://novest.me/truyen/" + slug,
            cover: cover,
            description: description,
            host: "https://novest.me"
        });
    }

    // Pagination
    var next = (parseInt(page) + 1).toString();

    return Response.success(data, next);
}