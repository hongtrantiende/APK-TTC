function execute(url, page) {
    if (!page) page = '1';
    let response;
    let list = [];
    let next = "";

    // 1. Gọi Ajax dựa trên loại URL
    if (url.includes("hoan-thanh.html")) {
        response = fetch("https://mongtruyen.com/sources/ajax/mong-truyen-truyen-danh-muc.php", {
            method: "POST",
            body: {
                "page": page,
                "limit": "12",
                "chuyenmuc": "193"
            }
        });
    } else if (url.includes("/the-loai/")) {
        let theloai = url.split("/the-loai/")[1].split("?")[0].replace(".html", "");
        response = fetch("https://mongtruyen.com/sources/ajax/ajax-pagination.php", {
            method: "POST",
            body: {
                "page": page,
                "limit": "24",
                "theloai": theloai,
                "do": "pagination_theloai"
            }
        });
    }

    if (response && response.ok) {
        // Lấy nội dung text thô của response
        let resText = response.text();
        
        // Parse chuỗi JSON thành Object
        let resJson = JSON.parse(resText);
        
        // Lấy mã HTML từ trường 'html' (dựa trên mẫu bạn gửi) hoặc 'data'/'noi_dung' (dự phòng cho thể loại)
        let htmlContent = resJson.html || resJson.data || resJson.noi_dung || "";
        
        if (htmlContent) {
            let doc = Html.parse(htmlContent);
            let elements = doc.select(".hydrosite-mong-truyen-book-card");

            elements.forEach(el => {
                let link = el.select(".hydrosite-mong-truyen-book-thumbnail a").first().attr("href");
                let cover = el.select(".hydrosite-mong-truyen-book-thumbnail img").first().attr("src");
                let name = el.select(".hydrosite-mong-truyen-book-title").text().trim();
                let author = el.select(".hydrosite-mong-truyen-book-author").text().trim();

                // Làm sạch tên truyện
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

            // Logic phân trang dựa trên total_page của JSON trả về
            let totalPage = parseInt(resJson.total_page || 0);
            let currentPage = parseInt(page);
            
            if (totalPage > currentPage) {
                next = (currentPage + 1).toString();
            } else if (list.length >= 12 && !totalPage) {
                // Dự phòng nếu không có total_page nhưng vẫn có đủ truyện
                next = (currentPage + 1).toString();
            }
        }
        
        return Response.success(list, next);
    }

    return null;
}