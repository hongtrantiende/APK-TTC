function execute(url) {
    var res = fetch(url);
    if (!res.ok) return null;

    var doc = res.html();

    // ===== NAME =====
    var name = doc.select("h1").first().text();

    // ===== AUTHOR =====
    var author = doc.select("a[href*='author']").first().text().trim();

    // ===== COVER =====
    // cover thường nằm ở img đầu trang (bạn có thể chỉnh lại nếu lệch)
    var cover = doc.select("img").first().attr("src");

    // ===== STATUS =====
    var status = doc.select("span:contains(Đang tiến hành), span:contains(Hoàn thành)").first().text();

    // ===== DESCRIPTION =====
    var desc = doc.select(".prose .whitespace-pre-line").text().trim();

    // ===== DETAIL (extra info) =====
    var detail = "";

    // Thể loại
    var genres = doc.select("a[href*='/the-loai/']");
    var genreList = [];
    for (var i = 0; i < genres.size(); i++) {
        genreList.push(genres.get(i).text());
    }

    // Quốc gia
    var nation = doc.select("a[href*='nation']").first().text();

    // Word count
    var words = doc.select("span:contains(từ)").first().text();

    detail += "Tác giả: " + author + "\n";
    detail += "Trạng thái: " + status + "\n";
    detail += "Quốc gia: " + nation + "\n";
    detail += "Số từ: " + words + "\n";
    detail += "Thể loại: " + genreList.join(", ");

    return Response.success({
        name: name,
        cover: cover,
        author: author,
        description: desc,
        detail: detail,
        host: "https://novest.me"
    });
}