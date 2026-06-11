load("config.js");

function execute() {
    return Response.success([
        { title: "Mới Đăng", input: BASE_URL + "/Truyen/moinhat/", script: "gen.js" },
        { title: "Xem Nhiều", input: BASE_URL + "/Truyen/luotxem/", script: "gen.js" },
        { title: "Đánh Giá", input: BASE_URL + "/Truyen/danhgia/", script: "gen.js" },
        { title: "Ngẫu Nhiên", input: BASE_URL + "/Truyen/ngaunhien/", script: "gen.js" }
    ]);
}