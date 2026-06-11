function execute(url) {
    //if(!page) page = '2';
    let response = fetch(url);// + "index_" + page + ".html");
    if (response.ok) {
        let doc = response.html('gb2312');
        const data = [];
		let table = doc.select("div.books div.bk")
        table.forEach(e => {
            data.push({
                name: e.select("a div.infos h3").first().text(),
                link: "https://www.qbtr.cc" + e.select("a").first().attr("href"),
                //cover: "https://www.qbtr.cc" + e.select("a div.pic img").first().attr("src"),
                description: e.select("div.booknews").text(),
                host: "https://www.qbtr.cc"
            })
        });
        //var next = page + 1 
        return Response.success(data)//, next)
    }
    return null;
}