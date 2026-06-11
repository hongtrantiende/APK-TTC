function execute(url) {
    var m = url.match(/truyen\/([^\/]+)/);
    if (!m) return Response.error("URL truyện không hợp lệ");
    var slug = m[1];
    
    var api = "https://truyen.codetheoyeucau.online/api/stories/by-slug/" + slug;
    var res = fetch(api);
    if (!res.ok) return Response.error("Không tải được chi tiết truyện");
    
    var data = res.json();
    
    var isOngoing = data.status === "ONGOING";
    var genresStr = data.genres ? data.genres.join(", ") : "";
    var detailText = "Tiến độ: " + (isOngoing ? "Đang ra" : "Hoàn thành") + "<br>Thể loại: " + genresStr;
    
    var info = {
        name: data.title,
        cover: data.coverUrl,
        host: "https://truyen.codetheoyeucau.online",
        author: data.authorName || data.authorSlug,
        description: data.shortDescription || data.description || "",
        detail: detailText,
        ongoing: isOngoing
    };
    
    // Add clickable tags (genres)
    var genreItems = [];
    var combinedTags = [];
    if (data.categories) {
        combinedTags = combinedTags.concat(data.categories);
    }
    if (data.genres) {
        combinedTags = combinedTags.concat(data.genres);
    }
    
    // Remove duplicates
    var uniqueTags = [];
    for (var i = 0; i < combinedTags.length; i++) {
        if (uniqueTags.indexOf(combinedTags[i]) === -1) {
            uniqueTags.push(combinedTags[i]);
            genreItems.push({
                title: combinedTags[i],
                input: "https://truyen.codetheoyeucau.online/api/search/stories?genres=" + encodeURIComponent(combinedTags[i]) + "&limit=20&page=",
                script: "gen.js"
            });
        }
    }
    if (genreItems.length > 0) {
        info.genres = genreItems;
    }
    
    // Add suggestions (e.g. Same author)
    if (data.authorName) {
        info.suggests = [
            {
                title: "Cùng tác giả",
                input: "https://truyen.codetheoyeucau.online/api/search/stories?q=" + encodeURIComponent(data.authorName) + "&limit=20&page=",
                script: "gen.js"
            }
        ];
    }
    
    return Response.success(info);
}
