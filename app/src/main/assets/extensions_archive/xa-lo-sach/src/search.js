load("config.js");
load("util.js");

function execute(key, page) {
	if (!page) page = '1';
	let url = BASE_URL + "/" + slugify(key) + "/tim-kiem.html?page=" + page;

	let doc = fetch(url).html();
	if (!doc) return Response.error("Failed to fetch data");

	let el = doc.select("li.story_item");
	let list = [];
	for (let i = 0; i < el.size(); i++) {
		let e = el.get(i);
		let style = e.select("a.story_img").attr("style");
		let cover = "";
		if (style) {
			let match = style.match(/url\(['"]?([^'"]+)['"]?\)/);
			if (match) cover = match[1];
		}
		if (cover && cover.startsWith("/")) cover = BASE_URL + cover;

		let link = e.select("a.story_title").attr("href");
		if (link && link.startsWith("/")) link = BASE_URL + link;

		list.push({
			name: e.select("a.story_title").text(),
			link: link,
			host: BASE_URL,
			cover: cover,
			description: e.select(".lst_5last .linkchap").text(),
		});
	}

	let next = "";
	if (el.size() > 0) {
		next = (parseInt(page) + 1).toString();
	}

	return Response.success(list, next);
}
