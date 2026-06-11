load('config.js');
function execute(url) {
    const doc = fetch(url).html();
    const cover = doc.select('.BGsectionOne-top-left img').first().attr('src');
    const name = doc.select('.BGsectionOne-top-right .title').text();
    const author = doc.select('.BGsectionOne-top-right .author a').text();
    const category = doc.select('.BGsectionOne-top-right .category a').text();
    const updateTime = doc.select('.BGsectionOne-top-right .time span').first().text();
    const status = doc.select('.BGsectionOne-top-right .time span').last().text();
    const latestChapterLink = doc.select('.BGsectionOne-top-right .newestChapter a').attr('href');
    
    const description = doc.select('.BGsectionTwo-bottom p').text();

    return Response.success({
        name,
        cover,
        author,
        category,
        updateTime,
        status,
        latestChapterLink,
        description,
        host: BASE_URL
    });
}
