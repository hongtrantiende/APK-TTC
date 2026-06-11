load("config.js");

function execute() {
	return Response.success([
		{
			title: "Mới cập nhật",
			input: `${BASE_URL}/danhsach/index.html?status=0&sort=2`,
			script: "home-content.js",
		},
		{
			title: "Đang hot",
			input: `${BASE_URL}/danhsach/index.html?status=0&sort=1`,
			script: "home-content.js",
		},
		{
			title: "Mới hoàn thành",
			input: `${BASE_URL}/danhsach/index.html?status=2&sort=2`,
			script: "home-content.js",
		}
	]);
}
