load("config.js");

function execute(url) {
	let doc = fetch(url).html();
	if (!doc) return null;

	let coverImg = doc.select(".wrap-content-image img").first().attr("src");
	if (!coverImg) coverImg = doc.select("img[itemprop=image]").attr("src");
	if (!coverImg) coverImg = doc.select(".book-img img").attr("src");
	if (!coverImg) coverImg = doc.select("img.img-thumbnail").attr("src");
	if (coverImg && coverImg.startsWith('/')) coverImg = BASE_URL + coverImg;

	let author = doc.select(".ten_tacgia").text();
	if (!author) author = doc.select("[itemprop=author]").text();
	if (!author) author = "Đang cập nhật";

	let name = doc.select("h1").text();

	let genres = [];
	let genreElements = doc.select('.list-info a[href*="/the-loai/"]');
	if (genreElements.size() === 0) genreElements = doc.select('.info-book a[href*="/the-loai/"]');
	for (let i = 0; i < genreElements.size(); i++) {
		genres.push({
			title: genreElements.get(i).text(),
			input: genreElements.get(i).attr("href"),
			script: "source.js"
		});
	}

	let views = doc.select(".fa-eye + span").text();
	if (!views) views = doc.select("li:contains(Lượt xem)").text();
	let statusText = doc.select("li:contains(Tình Trạng)").text() + doc.select(".info-book").text();
	let ongoing = statusText.indexOf("Đang tiến hành") !== -1;

	let description = "";
	let descEls = doc.select("h2:contains(Giới thiệu) ~ p");
	if (descEls.size() === 0) descEls = doc.select("h3:contains(Giới thiệu) ~ p");
	if (descEls.size() === 0) descEls = doc.select("div:contains(Giới thiệu) ~ p");
	for (let i = 0; i < descEls.size(); i++) {
		description += descEls.get(i).html() + "<br>";
	}
	if (!description) description = doc.select(".mota").html();
	if (!description) description = doc.select("#gioithieutruyen").html();
	if (!description) description = doc.select("[itemprop=description]").html();

	return Response.success({
		name: name,
		cover: coverImg,
		author: author,
		description: description,
		detail: `Tác giả: ${author}<br>Lượt xem: ${views}<br>Thể loại: ${genres.map(g => g.title).join(", ")}`,
		host: BASE_URL,
		ongoing: ongoing
	});
}
