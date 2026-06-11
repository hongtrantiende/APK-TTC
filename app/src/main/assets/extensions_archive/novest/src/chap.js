function execute(url) {
    var response = fetch(url);
    if (!response.ok) return null;

    var doc = response.html();

    // Lấy nội dung trong article
    var contentEl = doc.select("article.prose div");

    // Xóa mấy thẻ không cần (nếu có)
    contentEl.select("script, style, .ads, .banner").remove();

    // Lấy HTML
    var content = contentEl.html();

    // Clean nhẹ
    content = content
        .replace(/<p>\s*<\/p>/g, "")       // bỏ p rỗng
        .replace(/\n+/g, "")               // bỏ xuống dòng thừa
        .trim();

    return Response.success(content);
}