load("config.js");

function execute() {
	return Response.success([
		{ title: "Tiên Hiệp", input: BASE_URL + "/the-loai/tien-hiep.html", script: "genre-content.js" },
		{ title: "Huyền Huyễn", input: BASE_URL + "/the-loai/huyen-huyen.html", script: "genre-content.js" },
		{ title: "Đô Thị", input: BASE_URL + "/the-loai/do-thi.html", script: "genre-content.js" },
		{ title: "Khoa Huyễn", input: BASE_URL + "/the-loai/khoa-huyen.html", script: "genre-content.js" },
		{ title: "Kiếm Hiệp", input: BASE_URL + "/the-loai/kiem-hiep.html", script: "genre-content.js" },
		{ title: "Võng Du", input: BASE_URL + "/the-loai/vong-du.html", script: "genre-content.js" },
		{ title: "Dị Giới", input: BASE_URL + "/the-loai/di-gioi.html", script: "genre-content.js" },
		{ title: "Đồng Nhân", input: BASE_URL + "/the-loai/dong-nhan.html", script: "genre-content.js" },
		{ title: "Ngôn Tình", input: BASE_URL + "/the-loai/ngon-tinh.html", script: "genre-content.js" },
		{ title: "Cổ Đại", input: BASE_URL + "/the-loai/co-dai.html", script: "genre-content.js" },
		{ title: "Dã Sử", input: BASE_URL + "/the-loai/da-su.html", script: "genre-content.js" },
		{ title: "Hệ Thống", input: BASE_URL + "/the-loai/he-thong.html", script: "genre-content.js" },
		{ title: "Học Đường", input: BASE_URL + "/the-loai/hoc-duong.html", script: "genre-content.js" },
		{ title: "Xuyên Không", input: BASE_URL + "/the-loai/xuyen-khong.html", script: "genre-content.js" },
		{ title: "Hài Hước", input: BASE_URL + "/the-loai/hai-huoc.html", script: "genre-content.js" }
	]);
}
