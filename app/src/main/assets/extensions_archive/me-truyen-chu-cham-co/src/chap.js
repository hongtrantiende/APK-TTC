function execute(url) {
    let res = fetch(url);

    if (!res.ok) return null;

    let doc = res.html();

    // ===== LẤY NỘI DUNG =====
    let contentEl = doc.select("article").first();

    if (!contentEl) return null;

    // Xóa mấy tag không cần (nếu có)
    contentEl.select("script, style, iframe").remove();

    // Lấy HTML nội dung
    let content = contentEl.html();

    return Response.success(content);
}