function execute(url, page) {
    if(!page) page = '0';
    let response = fetch(url + "-" + page + ".html");
    if (response.ok) {
        let doc = response.html('gb2312');
        const data = [];
		let table = doc.select("div.books div.bk")
        table.forEach(e => {
            data.push({
                name: e.select("a div.infos h3").first().text(),
                link: "https://tongrenshe.cc" + e.select("a").first().attr("href"),
                cover: "https://tongrenshe.cc" + e.select("a div.pic img").first().attr("src"),
                description: e.select("div.infos p").text(),
                host: "https://tongrenshe.cc"
            })
        });
        var next = page + 1 
        return Response.success(data, next)
    }
    return null;
}