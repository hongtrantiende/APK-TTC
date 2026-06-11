function execute(url) {
	let doc = Http.get(url).html();
	if (!doc) return Response.error("Không thể tải trang: " + url);

	// Tiêu đề
	let nameElement = doc.select("h1.story-title").first();
	if (!nameElement) nameElement = doc.select("h3.title").first();
	if (!nameElement) nameElement = doc.select("[itemprop='name'] a").first();
	let name = nameElement ? nameElement.text() : "Unknown Title";

	// Tác giả
	let authorElement = doc.select("[itemprop='author']").first();
	if (!authorElement) authorElement = doc.select("a[href*='/tac-gia/']").first();
	let author = authorElement ? authorElement.text() : "Unknown";
	author = author.replace(/Tác giả\s*:?/gi, "").trim();

	// Ảnh bìa
	let coverElement = doc.select(".info-holder .book img, .book img, [itemprop='image']").first();
	let cover = coverElement ? coverElement.attr("src") : "";

	// Trạng thái
	let statusElement = doc.select(".info .text-primary, [itemprop='itemCondition']").first();
	let status = statusElement ? statusElement.text() : "";

	// Thể loại (Chỉ chọn trong khoang chứa text info để tránh trùng lặp thẻ ở menu/sidebar)
	let genresText = [];
	let genreElements = doc.select(".info [itemprop='genre']");
	if (genreElements.size() === 0) genreElements = doc.select(".info a[href*='/the-loai/']");
	if (genreElements.size() === 0) genreElements = doc.select("[itemprop='genre']");

	for (let i = 0; i < genreElements.size(); i++) {
		let gText = genreElements.get(i).text().trim();
		// Loại trừ các chữ thừa do match nhầm
		if (gText && gText.toLowerCase().indexOf("thể loại") === -1 && genresText.indexOf(gText) === -1) {
			genresText.push(gText);
		}
	}

	let descriptionElement = doc.select(".desc-text").first();
	if (!descriptionElement) descriptionElement = doc.select("[itemprop='description']").first();
	if (!descriptionElement) descriptionElement = doc.select(".desc").first();

	let description = descriptionElement ? descriptionElement.html() : "";

	let detailText = "Tác giả: " + author + "<br>";
	if (genresText.length > 0) detailText += "Thể loại: " + genresText.join(", ") + "<br>";
	if (status) detailText += "Trạng thái: " + status + "<br>";

	return Response.success({
		name: name,
		cover: cover,
		author: author,
		description: description,
		detail: detailText,
		host: "https://truyenmoiss.com",
		ongoing: status.toLowerCase().indexOf("hoàn thành") === -1 && status.toLowerCase().indexOf("full") === -1
	});
}
