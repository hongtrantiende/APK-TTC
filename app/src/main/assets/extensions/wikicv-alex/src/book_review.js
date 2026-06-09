load('config.js');
function execute(input, next) {
    let response = fetch(input, {method:"GET", headers: {"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36","Referer": BASE_URL + "/","Origin": BASE_URL,"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"vi-VN,vi;q=0.9,en;q=0.8"}});
    if (response.ok) {
        let doc = response.html();
        let comments = [];
        doc.select("#commentList > .review-group").forEach(e => {
            comments.push({
                name: e.select(".comment-content").first().select(".comment-username").text(),
                content:  e.select(".comment-content").first().select(".comment-content-msg").text(),
            });
        });

        return Response.success(comments, null);
    }

    return null;
}