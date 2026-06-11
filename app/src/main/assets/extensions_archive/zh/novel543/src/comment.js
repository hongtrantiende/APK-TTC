load("config.js");

function execute(bookId, page) {
    // Novel543 doesn't have a dedicated comment system
    // Try to parse any comments from the detail page
    var bookUrl = BASE_URL + "/" + bookId + "/";
    var doc = fetchCFLight(bookUrl);
    if (!doc) return Response.success([]);

    var comments = [];
    var commentEls = doc.select(".comment-list li, .comments .comment, #comments .item, .review-list li");
    for (var i = 0; i < commentEls.size(); i++) {
        var el = commentEls.get(i);
        var content = el.text().trim();
        if (!content || content.length < 5) continue;

        var userEl = selFirst(el, ".user, .username, .nickname, strong");
        var username = userEl ? userEl.text().trim() : "\u8b80\u8005";

        var dateEl = selFirst(el, ".date, .time, time");
        var date = dateEl ? dateEl.text().trim() : "";

        if (username && content.indexOf(username) === 0) content = content.substring(username.length).trim();

        comments.push({ username: username, content: content, date: date, avatar: "" });
        if (comments.length >= 50) break;
    }

    return Response.success(comments);
}
