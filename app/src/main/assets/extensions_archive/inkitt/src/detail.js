function execute(url) {
    // Inkitt thỉnh thoảng chặn bot, có thể cần thêm header User-Agent
    let response = fetch(url, {
        headers: {
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        }
    });

    if (response.ok) {
        let doc = response.html();
        
        // Bạn là chuyên gia, phần selector này bạn có thể F12 để điều chỉnh lại cho chuẩn xác nhất với giao diện hiện tại
        let name = doc.select("h1.story-title").text();
        if (!name) name = doc.select("h1").first().text(); // Fallback
        
        let author = doc.select("#storyAuthor").first().text();
        let cover = doc.select(".story-cover img").attr("src");
        if (!cover) cover = doc.select("meta[property=og:image]").attr("content");
        
        let description = doc.select(".story-summary, .summary").html();
        
        // Lấy thể loại (Genres)
        let genres = [];
        doc.select(".story-tags a").forEach(e => {
            genres.push(e.text());
        });

        return Response.success({
            name: name,
            cover: cover,
            author: author,
            description: description,
            detail: `Tác giả: ${author}<br>Thể loại: ${genres.join(", ")}`,
            host: "https://www.inkitt.com"
        });
    }
    return null;
}