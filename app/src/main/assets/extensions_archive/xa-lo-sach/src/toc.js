load("config.js");

function execute(url) {
	let response = fetch(url);
	if (!response.ok) return Response.success([]);
	let html = response.text();

	let list = [];

	// Khoanh vùng khu vực chứa chương để tránh quét rác
	let chapListRegex = /<(?:ul|div)[^>]*class="(?:list-chapter|chap-item|lst-chapter|danhsachchuong)[^>]*>([\s\S]*?)<\/(?:ul|div)>/i;
	let listMatch = chapListRegex.exec(html);
	let chapArea = html;
	if (!listMatch) {
		let divMatch = /<div[^>]*id="danhsachchuong"[^>]*>([\s\S]*?)<\/div>/i.exec(html);
		if (divMatch) chapArea = divMatch[1];
	} else {
		chapArea = listMatch[1];
	}

	// Match tất cả các thẻ a
	let chapRegex = /href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/ig;
	let match;
	let added = 0;
	while ((match = chapRegex.exec(chapArea)) !== null) {
		let link = match[1];
		let name = match[2];

		// Loại bỏ HTML thừa bên trong tên chương (nếu có)
		name = name.replace(/<[^>]+>/g, "").trim();

		if (link && link.indexOf("P=") === -1 && link.indexOf("p=") === -1 && link.indexOf("javascript") === -1 && name.length > 0) {
			let fullLink = link.startsWith("/") ? BASE_URL + link : (link.startsWith("http") ? link : BASE_URL + "/" + link);
			list.push({
				name: name,
				url: fullLink,
				host: BASE_URL
			});
			added++;
		}
	}

	// Fallback: nếu xui regex không ăn thì parse DOM (Rất hạn chế dùng để đảm bảo tốc độ tối đa)
	if (added === 0) {
		let doc = Html.parse(html);
		let el = doc.select(".lst-chapter .chap-item a");
		if (el.size() === 0) el = doc.select("#danhsachchuong .list-chapter li a");
		if (el.size() === 0) el = doc.select("#danhsachchuong a:not(.pager a):not(.pagination a)");

		for (let i = 0; i < el.size(); i++) {
			let e = el.get(i);
			let link = e.attr("href");
			let name = e.text();

			if (link && link.indexOf("P=") === -1 && link.indexOf("p=") === -1 && link.indexOf("javascript") === -1 && name.length > 0) {
				list.push({
					name: name,
					url: link.startsWith("/") ? BASE_URL + link : (link.startsWith("http") ? link : BASE_URL + "/" + link),
					host: BASE_URL
				});
			}
		}
	}

	// Loại bỏ duplicates nếu có (dùng Object key vì Set có thể gây lỗi trên Vbook Js Engine cũ)
	let uniqueList = [];
	let urls = {};
	for (let i = 0; i < list.length; i++) {
		if (!urls[list[i].url]) {
			uniqueList.push(list[i]);
			urls[list[i].url] = true;
		}
	}

	return Response.success(uniqueList);
}
