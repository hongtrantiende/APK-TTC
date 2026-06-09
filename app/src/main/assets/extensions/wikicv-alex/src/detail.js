load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(url, {method:"GET", headers: {"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36","Referer": BASE_URL + "/","Origin": BASE_URL,"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"vi-VN,vi;q=0.9,en;q=0.8"}});
    if (response.ok) {
        let doc = response.html();

        let genres = [];
        doc.select(".book-desc > p").first().select("a").forEach(e => {
            genres.push({
                title: e.text(),
                input: BASE_URL + e.attr('href'),
                script: "gen.js"
            });
        });

        let detail = '';
        doc.select(".cover-info > div").first().select("p").first().remove();
        doc.select(".cover-info > div").first().select("p").forEach(e => {
            detail += e.text() + "<br>";
        });

        let bookId = doc.select("input[name=bookId]").first().attr("value");

        let name = doc.select(".cover-info h2").text();
        let author = doc.select(".cover-info a[href*=tac-gia]").text();

        let comments = [];
        let review = doc.select("a[href*=/review/truyen]").attr("href");
        if (review) {
            comments.push({
                title: "Đánh giá",
                input: BASE_URL + review,
                script: "book_review.js"
            });
        }

        comments.push({
            title: "Bình luận",
            input: BASE_URL + "/comment?bookId=" + bookId + "&chapterId&order=newest",
            script: "comment.js"
        });
        return Response.success({
            name: name,
            cover: doc.select("div.book-info img").first().attr("src"),
            author: author,
            description: doc.select("div.book-desc-detail").html(),
            detail: detail,
            host: BASE_URL,
            ongoing: doc.select(".cover-info").html().indexOf("Còn tiếp") > 0,
            genres: genres,
            suggests: [
                {
                    title: "Cùng thể loại",
                    input: doc.select(".desktop-similar-books").html(),
                    script: "similar.js"
                }
            ],
            comments: comments
        });
    }
    return null;
}