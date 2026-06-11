load('config.js');

function execute(url) {
    let txtUrl = queryParam(url, "txt");
    let storyUrl = queryParam(url, "story") || BASE_URL + "/";
    let index = parseInt(queryParam(url, "i") || "0");
    if (!txtUrl && isTxtUrl(url)) txtUrl = url.replace(/[?#].*$/, "");
    if (!txtUrl) return Response.error("缺少 TXT 链接。");

    let text = getText(txtUrl, storyUrl);
    if (!text || cleanText(text).length < 20) return loadError();

    let chapters = splitChapters(text);
    if (isNaN(index) || index < 0 || index >= chapters.length) index = 0;
    let chapter = chapters[index];
    let content = textToHtml(chapter.body);
    if (!content || cleanText(content).length < 20) return Response.error("未找到章节内容。");
    return Response.success("<h2>" + htmlEscape(tocName(chapter, index)) + "</h2>\n" + content);
}
