load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let cvData ="";
    let part1 = url.replace(BASE_URL, "").replace(BASE_URL, "").replace(".html","");
    var next = part1;
    while (next.includes(part1)) {
        let response = fetch(BASE_URL + next +".html");
        if (response.ok) {
            let doc = response.html();
            next = doc.select("a:contains(下一)").attr("href").replace(".html","");
            let htm = doc.select("#chaptercontent").html();
            htm = htm.replace(/\&nbsp;/g, "");
            cvData = cvData + htm;
        } else {
            break;
        }
    }
    if (cvData) {
        return Response.success(cvData);
    }
    return null;
}
function cleanHtml(htm) {
    htm = htm.replace(/(<br>\s*){2,}/g, '<br>');
    htm = htm.replace(/<a[^>]*>([^<]+)<\/a>/g, '');
    htm = htm.replace(/&(nbsp|amp|quot|lt|gt);/g, "");
    htm = htm.replace(/<!--(<br \/>)?[^>]*-->/gm, '');
    htm = htm.replace(/\&nbsp;/g, "");
    htm = htm.replace("请收藏：https://m.bqgma.com", "");
    return htm;
}