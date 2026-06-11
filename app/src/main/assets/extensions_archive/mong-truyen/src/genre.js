function execute() {
    // Gửi request đến trang chứa danh sách thể loại (thường là trang chủ hoặc trang danh mục)
    let response = fetch("https://mongtruyen.com/the-loai.html"); 

    if (response.ok) {
        let doc = response.html();
        let genres = [];
        
        // Selector nhắm vào class item link của từng thể loại
        let menu = doc.select("a.hydrosite-mong-truyen-categories-list-item-link");
        
        menu.forEach(e => {
            // Lấy tên thể loại từ thẻ span bên trong
            let title = e.select(".hydrosite-mong-truyen-categories-list-item-link-text").text().trim();
            // Lấy link thể loại
            let link = e.attr("href");
            
            if (title && link) {
                genres.push({
                    title: title,
                    input: link, // Link này sẽ được truyền vào hàm execute của gen.js
                    script: "gen.js"
                });
            }
        });

        return Response.success(genres);
    }

    return null;
}