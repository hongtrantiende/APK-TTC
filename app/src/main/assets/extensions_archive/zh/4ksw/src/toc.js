load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    if (url.slice(-1) !== "/")
        url = url + "/";
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('gbk'); // Đảm bảo charset GBK đã được xử lý
        let el = doc.select("body > div.container > div:nth-child(4) > div > div > div.panel-body > ul > li");
        const data = [];
        for (let i = 0; i < el.size(); i++) {
            var e = el.get(i);
            data.push({
                name: e.select("a").text(),
                url: BASE_URL + e.select("a").attr("href"),
                host: BASE_URL
            });
        }
        return Response.success(data);
    }
    return null;
}