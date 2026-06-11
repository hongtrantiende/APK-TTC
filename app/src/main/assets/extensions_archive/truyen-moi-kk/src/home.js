function execute() {
	return Response.success([
		{ title: "Truyện Hot", input: "https://truyenmoiii.org/danh-sach/truyen-hot", script: "home-content.js" },
		{ title: "Truyện Mới Cập Nhật", input: "https://truyenmoiii.org/danh-sach/truyen-moi", script: "home-content.js" },
		{ title: "Truyện Hoàn Thành", input: "https://truyenmoiii.org/danh-sach/truyen-full", script: "home-content.js" }
	]);
}
