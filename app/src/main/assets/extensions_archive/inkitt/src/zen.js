load('config.js');

function execute(url, page) {
    if (!page) page = 0;
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let data = [];

        // Chọn các thẻ div có chứa thuộc tính data-story-index (Bỏ qua hoàn toàn các class rác sinh tự động)
        doc.select("div[data-story-index]").forEach(e => {
            // Lấy thông tin từ các thẻ data-* cực kỳ an toàn
            let name = e.attr("data-title");
            let cover = e.attr("data-cover-url");
            let summary = e.attr("data-summary");

            // Lấy link truyện (bắt chính xác thẻ a có chứa link /stories/)
            let link = e.select("a[href*=/stories/]").first().attr("href");

            // Lấy tên tác giả (vì class bị mã hóa nên ta dùng bộ lọc thẻ 'a' có chứa chữ "by ")
            let authorEl = e.select("a:contains(by )").first();
            let author = authorEl ? authorEl.text() : "";

            // Xử lý phần mô tả hiển thị trên app (Gộp Tác giả + Tóm tắt)
            let description = author;
            if (summary) {
                description = description ? (description + " • " + summary) : summary;
            }

            if (name && link) {
                data.push({
                    name: name,
                    link: BASE_URL + link,
                    cover: cover,
                    description: description,
                    host: "https://www.inkitt.com"
                });
            }
        });

        return Response.success(data);
    }

    return null;
}