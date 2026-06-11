function execute(url, page) {
    if(!page) page = '1';
    let response = fetch("https://lnovel.org/books?page=" + page + "&q%5Bgenres_id_eq%5D=" + url);
    if(!url) response = 'https://lnovel.org/books';
    if (response.ok) {
        let doc = response.html('utf-8');
        const data = [];
		let table = doc.select("a.col-6.col-md-4.col-md-3.col-lg-6.col-xl-4")
        table.forEach(e => {
            data.push({
                name: e.select("a.col-6.col-md-4.col-md-3.col-lg-6.col-xl-4").first().text(),
                link: e.select("a").first().attr("href"),
                cover: e.select("img").first().attr("src"),
                host: "https://lnovel.org/"
            })
        });
        let next = doc.select("ul.pagination.justify-content-center.mb-0").select("li.page-item.active +li").text()
        return Response.success(data, next)
    }
    return null;
}