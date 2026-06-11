function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        
        // Khối chứa nội dung truyện
        let content = doc.select("#chapterText");
        
        // Parse lại HTML cho sạch đẹp để đưa vào reader của vBook
        let html = content.html();
        // Xử lý các ký tự xuống dòng đặc thù của nền tảng (nếu có)
        html = html.replace(/\n/g, "<br>");
        
        return Response.success(html);
    }
    return null;
}