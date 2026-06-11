load("config.js");

function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        
        // Extract book information
        let bookTitle = doc.select("#bookinfo .booktitle h1").text();
        let author = doc.select("#author a").text();
        let cover = doc.select("#bookimg img").attr("_src") || doc.select("#bookimg img").attr("src");
        let description = doc.select("#bookintro p").html();
        let status = doc.select(".count li:contains('狀') span").text();
        let category = doc.select(".count li:contains('小') span").text();
        
        // Make sure cover is absolute URL
        if (cover && !cover.startsWith("http")) {
            cover = BASE_URL + cover;
        }
        
        // Extract genres (categories) - this site doesn't seem to have clickable genre links in detail page
        let genres = [];
        if (category) {
            genres.push({
                title: category,
                input: BASE_URL + "/" + category.toLowerCase() + "/",
                script: "source.js"
            });
        }
        
        // Get additional info
        let updateTime = doc.select(".new .uptime span").text();
        let wordCount = doc.select(".count li:contains('字') span").text();
        let clickCount = doc.select(".count li:contains('點') span").text();
        
        let detail = `
            <div>
                <p><strong>分類:</strong> ${category}</p>
                <p><strong>狀態:</strong> ${status}</p>
                <p><strong>字數:</strong> ${wordCount}</p>
                <p><strong>點擊:</strong> ${clickCount}</p>
                <p><strong>更新時間:</strong> ${updateTime}</p>
            </div>
        `;
        
        return Response.success({
            name: bookTitle,
            cover: cover,
            author: author,
            description: description,
            genres: genres,
            detail: detail,
            ongoing: status.indexOf("完") === -1, // Nếu không chứa "完" thì đang tiếp tục
            host: BASE_URL,
        });
    }
    return null;
}