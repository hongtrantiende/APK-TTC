function execute(url) {
    // The URL passed from toc.js is already the API endpoint!
    // Example: https://truyen.codetheoyeucau.online/api/chapters/cmmhtmebd00035y0fqe70iykg/1
    var res = fetch(url);
    if (!res.ok) return Response.error("Lỗi tải chương: " + res.status);
    
    var json = res.json();
    var content = json.contentMd || json.contentHtml || json.content || json.text;
    
    if (!content) {
        return Response.success("<p>Không có nội dung chương</p>");
    }
    
    // Formatting plain text/Markdown to basic HTML for reader
    var html = content.replace(/\r\n/g, "\n").replace(/\n/g, "<br>");
    
    return Response.success(html);
}
