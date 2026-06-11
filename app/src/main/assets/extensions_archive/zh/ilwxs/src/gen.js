function execute(url, page) {
    if(!page) page = '1';
	let response = fetch("https://www.ilwxs.com" + url + page);
	if (response.ok) {
		let doc = response.html('utf-8');
		const data = [];
		let table = doc.select("div#alistbox")
		table.forEach(e => {
			data.push({
				name: e.select(".info .title h2").first().text(),
				link: e.select("div.info .title a").first().attr("href"),
				cover: e.select(".pic a img").first().attr("src"),
				description: e.select("div.info .title span").text(),
				host: "https://www.ilwxs.com"
			})
		});
		var next = doc.select("#pagelink a.next").attr("href").split(url)[1]
		return Response.success(data, next)
	}
}