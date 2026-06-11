function execute(url) {
    // Gửi request lấy nội dung trang chi tiết truyện
    let response = fetch(url);

    if (response.ok) {
        let doc = response.html();
        
        // 1. Lấy tên truyện
        let name = doc.select(".mdv-san-pham-show-name").text().trim();

        let id_truyen = doc.select("input[name='id_truyen']").attr("value");
        let token = doc.select("input[name='pnvn_token']").attr("value");

        // 2. Lấy tác giả
        // Tìm thẻ a có class 'tac-gia-ten'
        let author = doc.select("a.tac-gia-ten").text().trim();

        // 3. Lấy ảnh bìa (Cover)
        // Lưu ý: Thẻ img thường nằm trong class '.hydrosite-mong-truyen-book-thumbnail'
        let cover = doc.select(".san-pham-book-item-show-image img").first().attr("src");

        // 4. Lấy tình trạng truyện
        let status = doc.select(".thong-tin-tinh-trang-text").text().trim();

        // 5. Lấy mô tả truyện (Description)
        // Dựa theo code trước đó bạn đưa, description nằm trong class catchuoi4
        let description = doc.select(".mdv-san-pham-show-gioi-thieu-des").text().trim();

        // 6. Thông tin chi tiết khác (Gộp Tác giả, Tình trạng, Thể loại vào info)
        // Lấy danh sách các thể loại
        let genres = [];
        doc.select(".san-pham-the-loai-item a").forEach(genreEl => {
            genres.push(genreEl.text().trim());
        });

        // Tạo chuỗi detail hiển thị trong app
        let detailInfo = "Tác giả: " + author + "<br>" +
                         "Tình trạng: " + status + "<br>" +
                         "Thể loại: " + genres.join(", ");

        return Response.success({
            name: name,
            cover: cover,
            author: author,
            description: description,
            detail: detailInfo,
            host: "https://mongtruyen.com", 
            input: JSON.stringify({
                url: url,
                id: id_truyen,
                token: token
            })
        });
    }

    return null;
}