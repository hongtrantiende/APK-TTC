load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);


    const doc = fetch(url).html();
    var content = doc.select(".readcontent").html();
    var nextPage = doc.select('.text-center a').last();
    while(nextPage.text() === '下一页'){
        var doc2 = fetch(BASE_URL+nextPage.attr('href')).html();
        content += doc2.select(".readcontent").html();
        var nextPage = doc2.select('.text-center a').last();
    }
    content = content.replace(/<p><\/p>/g,'')
    return Response.success(content);
}