load('config.js');
function execute(url) {
    let cvData ="";
    let part1 = url.replace(BASE_URL, "").replace(".html","");
    var next = part1;
    while (next.includes(part1)) {
        let url1 = BASE_URL + next + ".html"
        let response = fetch(url1);
        if (response.ok) {
            let doc = response.html();
            let doc2 = doc.html()
            next = (match = doc2.match(/next_page = "(.*?)"/)) ? match[1] : "0";
            next = next.replace(".html","");
            let htm = doc.select(".content").html();
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