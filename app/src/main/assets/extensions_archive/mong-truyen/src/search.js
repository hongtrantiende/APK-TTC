function execute(key) {
    // Gửi request tìm kiếm với tham số q
    let response = fetch("https://mongtruyen.com/search.html?q=" + key);

    if (response.ok) {
        let doc = response.html();
        let list = [];

        // Chọn tất cả các khối card truyện trong kết quả tìm kiếm
        let elements = doc.select(".search-results-grid .hydrosite-mong-truyen-book-card");

        elements.forEach(el => {
            // Lấy link truyện
            let link = el.select(".hydrosite-mong-truyen-book-thumbnail a").first().attr("href");
            
            // Lấy ảnh bìa
            let cover = el.select(".hydrosite-mong-truyen-book-thumbnail img").first().attr("src");
            
            // Lấy tên truyện
            let name = el.select(".hydrosite-mong-truyen-book-title").text().trim();
            
            // Lấy tác giả
            let author = el.select(".hydrosite-mong-truyen-book-author").text().trim();

            // Làm sạch tên truyện (xóa phần "- Chương xxx" nếu web tự chèn vào title)
            if (name.includes("- Chương")) {
                name = name.split("- Chương")[0].trim();
            }

            if (link && name) {
                list.push({
                    name: name,
                    link: link,
                    cover: cover,
                    description: author,
                    host: "https://mongtruyen.com"
                });
            }
        });

        // Tính toán phân trang
        // Kiểm tra xem có trang tiếp theo không dựa vào thẻ phân trang
        let next = "";
        let nextButton = doc.select("ul.pagination li.active + li a").first();
        if (nextButton.text()) {
            next = (parseInt(page) + 1).toString();
        }

        return Response.success(list, next);
    }

    return null;
}