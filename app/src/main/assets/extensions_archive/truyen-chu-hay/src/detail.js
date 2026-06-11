function execute(url) {
    if (url.startsWith("/")) {
        url = "https://truyenchuhay.org" + url;
    }
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let name = doc.select("h1").text().replace(" - Truyện Chữ", "").trim();
        let author = doc.select("a[href*='/tac-gia/']").text().trim();
        let cover = doc.select("img.bg-shadow-black").attr("src")
            || doc.select("img[itemprop='image']").attr("src")
            || doc.select("img[itemProp='image']").attr("src");

        let description = doc.select("#gioi-thieu-truyen .prose").html();
        if (!description) {
            description = doc.select(".prose.max-w-none.text-justify").html();
        }

        let detail = "";
        let genres = [];
        doc.select("li a[href*='/the-loai/']").forEach(e => {
            genres.push(e.text().trim());
        });

        if (genres.length > 0) {
            detail += "Thể loại: " + genres.join(", ") + "<br>";
        }

        doc.select("span.col-span-1").forEach(e => {
            let text = e.text().toLowerCase();
            if (text.includes("chương")) {
                let count = e.select("strong").text().trim();
                if (count) detail += "Số chương: " + count + "<br>";
            } else if (text.includes("trạng thái")) {
                let status = e.select("strong").text().trim();
                if (status) detail += "Trạng thái: " + status + "<br>";
            } else if (text.includes("lượt xem")) {
                let views = e.select("strong").text().trim();
                if (views) detail += "Lượt xem: " + views + "<br>";
            }
        });

        let host = "https://truyenchuhay.org";

        return Response.success({
            name: name,
            cover: cover || host + "/images/logo.png",
            author: author,
            description: description,
            detail: detail,
            host: host
        });
    }
    return null;
}
