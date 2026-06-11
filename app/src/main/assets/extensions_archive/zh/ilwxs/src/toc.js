
function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('utf-8');
        const data = [];
		let table = doc.select("div#list dl dd")
        table.forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                url: "https://www.ilwxs.com/" + e.select("a").first().attr("href"),
                host: "https://www.ilwxs.com/"
            })
        });
        //let next = doc.select("div.chapter-page").attr("id").split("page-")[1]
        return Response.success(data)
    }
    return null;
}