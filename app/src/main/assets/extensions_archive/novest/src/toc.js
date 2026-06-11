function execute(url) {
    var response = fetch(url);
    if (!response.ok) return null;

    var doc = response.html();
    var data = [];

    // Selector chapter
    var items = doc.select("a.group\\/chapter");

    for (var i = 0; i < items.size(); i++) {
        var e = items.get(i);

        var name = e.attr("title"); // lấy từ title (chuẩn nhất)

        var slug = e.attr("href");

        data.push({
            name: name,
            url: "https://novest.me" + slug
        });
    }

    return Response.success(data);
}