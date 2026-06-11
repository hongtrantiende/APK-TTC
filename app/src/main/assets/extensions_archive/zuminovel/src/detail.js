load("config.js");

function execute(url) {
    url = normalizeUrl(url);
    var res = getHtml(url);
    var loginError = loginErrorIfNeeded(res);
    if (loginError) return loginError;
    if (!res.ok) return Response.error("Cannot load: " + res.status);
    var html = res.text();
    var book = extractBookJson(html);
    if (!book) return Response.error("No book data found");
    var genres = [];
    var list = book.genre || [];
    for (var i = 0; i < list.length; i++) {
        var title = cleanText(list[i]);
        if (title) genres.push({ title: title, input: BASE_URL + "/list?tag=" + encodeURIComponent(title), script: "gen.js" });
    }
    return Response.success({
        name: cleanText(book.name),
        cover: normalizeUrl(book.image || ""),
        host: BASE_URL,
        author: cleanText(book.author && book.author.name ? book.author.name : ""),
        description: ((book.description || "") + "").replace(/\n/g, "<br>"),
        detail: "Số chương: " + cleanText(book.numberOfPages || ""),
        ongoing: cleanText(book.status || "") !== "completed",
        genres: genres
    });
}
