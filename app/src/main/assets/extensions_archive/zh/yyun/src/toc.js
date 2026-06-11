function execute(url) {
	url = url.replace('m.yanyunxs.com', 'www.yanyunxs.com');
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let el = doc.select("#list dd a")
        const data = [];
        for (let i = 9;i < el.size(); i++) {
            var e = el.get(i);
            data.push({
                name: e.select("a").text(),
                url: e.attr("href"),
                host: "https://www.yanyunxs.com"
            })
        }
        return Response.success(data);
    }
    return null;
}