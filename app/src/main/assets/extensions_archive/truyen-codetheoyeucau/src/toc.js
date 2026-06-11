function execute(url) {
    var m = url.match(/truyen\/([^\/]+)/);
    if (!m) return Response.error("URL truyện không hợp lệ");
    var slug = m[1];
    
    // 1. Fetch story details to get the internal ID
    var apiDetail = "https://truyen.codetheoyeucau.online/api/stories/by-slug/" + slug;
    var resDetail = fetch(apiDetail);
    if (!resDetail.ok) return Response.error("Lỗi lấy thông tin truyện (TOC)");
    var dataDetail = resDetail.json();
    var storyId = dataDetail.id;
    
    // 2. Fetch all chapters using the ID
    var apiToc = "https://truyen.codetheoyeucau.online/api/stories/" + storyId + "/chapters?limit=10000";
    var resToc = fetch(apiToc);
    if (!resToc.ok) return Response.error("Lỗi lấy danh sách chương: " + resToc.status);
    
    var dataToc = resToc.json();
    var chapters = dataToc.items || dataToc; // Handle case where it might just return array or wrapped in items
    if (!Array.isArray(chapters) && dataToc.chapters) chapters = dataToc.chapters;
    if (!Array.isArray(chapters)) chapters = [];
    
    var list = [];
    for (var i = 0; i < chapters.length; i++) {
        var c = chapters[i];
        var chapIndex = c.index !== undefined ? c.index : (i + 1);
        
        // Pass the storyId and index to the chapter URL so chap.js can build the API endpoint
        var chapLink = "https://truyen.codetheoyeucau.online/api/chapters/" + storyId + "/" + chapIndex;
        
        list.push({
            name: c.title || ("Chương " + chapIndex),
            url: chapLink,
            host: "https://truyen.codetheoyeucau.online"
        });
    }
    
    return Response.success(list);
}
