function execute(url) {
    if (url.startsWith("/")) {
        url = "https://truyenchuhay.org" + url;
    }

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let chapters = [];

        let list = doc.select("#danh-sach-chuong ul.custom-list li a");
        list.forEach(e => {
            chapters.push({
                name: e.select("strong").text().trim(),
                url: e.attr("href"),
                host: "https://truyenchuhay.org"
            });
        });

        if (chapters.length === 0) return null;

        return Response.success(chapters);
    }

    return null;
}
