function execute(url) {

    var browser = Engine.newBrowser();
    var response = browser.launch(url, 5000).html(); // Hàm này trả về String HTML
    browser.close();

    var doc = Html.parse(response);

    var html = doc.html();

    var start = html.indexOf("</audio>");
    var end = html.indexOf('<p class="p-2 text-primary');

    if (start == -1 || end == -1) return null;

    var content = html.substring(start + 8, end).trim();

    content = content.replace(/<(?!br\s*\/?)[^>]+>/gi, "").replace(/\\n/g, "").replace(/\\\//g, "/").trim();

    return Response.success(content);
}