function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        // ===== NAME =====
        let name = doc.select("h1.font-bold").first().text();

        // ===== AUTHOR =====
        let author = doc.select("a[href^=/tac-gia/] span").first().text();

        // ===== COVER =====
        let cover = doc.select("img.aspect-\\[3\\/4\\]").first().attr("src");

        // ===== STATUS + GENRE =====
        let status = "Đang ra";
        let ongoing = true;
        let genres = [];

        let items = doc.select("ul.flex li");

        items.forEach(item => {
            let text = item.text().trim();

            if (text.includes("Đang ra")) {
                status = "Đang ra";
                ongoing = true;
            } else if (text.includes("Hoàn thành")) {
                status = "Hoàn thành";
                ongoing = false;
            }

            let link = item.select("a");
            if (link.size() > 0) {
                genres.push(link.text().trim());
            }
        });

        // ===== DESCRIPTION =====
        let description = doc.select("div.prose").text().trim();

        // ===== DETAIL (gộp status + genre) =====
        let detail = "Trạng thái: " + status;
        if (genres.length > 0) {
            detail += " | Thể loại: " + genres.join(", ");
        }

        return Response.success({
            name: name,
            cover: cover,
            author: author,
            description: description,
            detail: detail,
            ongoing: ongoing,
            host: "https://metruyenchu.co"
        });
    }
    return null;
}