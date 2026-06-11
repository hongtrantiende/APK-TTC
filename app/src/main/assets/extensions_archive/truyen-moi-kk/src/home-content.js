function execute(url, page) {
	if (!page) page = '1';

	let fetchUrl = url + "/trang-" + page;
	let doc = Http.get(fetchUrl).html();

	if (!doc) return Response.error("Không thể tải trang: " + fetchUrl);

	let el = doc.select(".list-truyen .row[itemscope]");
	let novelList = [];

	for (let i = 0; i < el.size(); i++) {
		let e = el.get(i);
		novelList.push({
			name: e.select(".truyen-title a").text(),
			link: e.select(".truyen-title a").attr("href"),
			description: e.select("[itemprop='author']").text(),
			cover: e.select(".lazyimg").attr("data-image") || e.select("img").attr("src") || e.select("[data-src]").attr("data-src"),
			host: "https://truyenmoiss.com"
		});
	}

	let next = "";
	let nextUrl = doc.select(".pagination li.active + li a").attr("href");
	if (nextUrl) {
		let match = nextUrl.match(/trang-(\d+)/);
		if (match) next = match[1];
	}

	return Response.success(novelList, next);
}
