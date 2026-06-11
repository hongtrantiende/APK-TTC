function execute(url) {
    // 1. Tải trang chi tiết truyện để bóc tách thông số bảo mật
    let response = fetch(url);
    
    if (response.ok) {
        let doc = response.html();
        
        // Lấy ID truyện và Token từ các thẻ input hidden trên trang
        let id_truyen = doc.select("input[name='id_truyen']").attr("value");
        let pnvn_token = doc.select("input[name='pnvn_token']").attr("value");

        // Nếu không lấy được ID thì không thể gọi Ajax
        if (!id_truyen) return null;

        let allChapters = [];
        let page = 1;
        let hasNext = true;

        // 2. Vòng lặp gọi Ajax lấy danh sách chương theo từng trang
        while (hasNext) {
            let ajaxRes = fetch("https://mongtruyen.com/sources/ajax/load-chapters.php", {
                method: "POST",
                headers: {
                    "content-type": "application/x-www-form-urlencoded; charset=UTF-8",
                    "x-requested-with": "XMLHttpRequest",
                    "referer": url
                },
                body: {
                    "page": page,
                    "id_truyen": id_truyen,
                    "pnvn_token": pnvn_token
                }
            });

            if (ajaxRes.ok) {
                let resHtml = ajaxRes.text();
                
                // Kiểm tra nếu nội dung trả về trống hoặc không chứa link chương thì dừng
                if (!resHtml || resHtml.trim() === "" || !resHtml.includes("<a")) {
                    hasNext = false;
                } else {
                    let ajaxDoc = Html.parse(resHtml);
                    let chapters = ajaxDoc.select("a");
                    
                    chapters.forEach(chap => {
                        allChapters.push({
                            name: chap.text().trim(),
                            url: chap.attr("href"),
                            host: "https://mongtruyen.com"
                        });
                    });

                    page++; // Sang trang Ajax kế tiếp
                }
            } else {
                hasNext = false;
            }

            // Giới hạn an toàn tránh loop vô tận
            if (page > 500) hasNext = false;
        }

        return Response.success(allChapters);
    }

    return null;
}