
function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('gb2312');
        const data = [];
		let table = doc.select("div.book_list ul li a")
        table.forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                url: e.select("a").first().attr("href"),
                host: "https://tongrenshe.cc/"
            })
        });
        //let next = doc.select("div.chapter-page").attr("id").split("page-")[1]
        return Response.success(data)
    }
    return null;
}