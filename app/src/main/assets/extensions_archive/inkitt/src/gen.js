function execute(url, page) {
    if (!page) page = '1';

    // 1. Chuẩn bị API URL từ URL giao diện ban đầu
    // Ví dụ đầu vào: https://www.inkitt.com/genres/action
    // Đổi thành: https://www.inkitt.com/1/genres/action
    let apiUrl = url.replace("inkitt.com/", "inkitt.com/1/");
    
    // Nối thêm các tham số lấy từ curl
    let queryParams = `page=${page}&sorting=popular_all_time&story_type=original`;
    if (apiUrl.includes("?")) {
        apiUrl += "&" + queryParams;
    } else {
        apiUrl += "?" + queryParams;
    }

    // 2. Gọi API với các header giả lập trình duyệt
    let response = fetch(apiUrl, {
        method: "GET",
        headers: {
            "accept": "application/json",
            "accept-language": "en-US,en;q=0.9,vi;q=0.8",
            "content-type": "application/json",
            "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0"
        }
    });

    if (response.ok) {
        // 3. Phân tích cú pháp JSON
        let json = response.json();
        let books = [];
        
        // Thường Inkitt sẽ trả danh sách truyện ở mảng `stories` hoặc `data`
        let storyList = json.stories || json.data || [];

        storyList.forEach(item => {
            // Lấy các key tương ứng của Inkitt (dựa trên format data-* ở thẻ HTML trước đó)
            let name = item.title || item.name;
            let id = item.id || item.story_id;
            let summary = item.summary || item.teaser || "";
            let author = item.user ? item.user.name : (item.author_name || "");
            
            // Link ảnh bìa (ưu tiên vertical cover)
            let cover = item.vertical_cover_url || item.cover_url || item.cover.url || "";
            
            if (name && id) {
                books.push({
                    name: name,
                    // Link chi tiết của truyện thường có dạng /stories/{id}
                    link: `https://www.inkitt.com/stories/${id}`,
                    cover: cover,
                    description: summary ? summary: `Author: ${author}`,
                    host: "https://www.inkitt.com"
                });
            }
        });

        // 4. Xử lý chuyển trang
        let next = "";
        // Nếu số lượng truyện trả về > 0, cho phép tải trang tiếp theo
        if (books.length > 0) {
            next = (parseInt(page) + 1).toString();
        }

        return Response.success(books, next);
    }

    return null;
}