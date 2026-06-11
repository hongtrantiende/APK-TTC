function execute() {
    return Response.success([
        { title: "Truyện Mới Cập Nhật", input: "/danh-sach/truyen-moi", script: "gen.js" },
        { title: "Truyện Hot", input: "/danh-sach/truyen-hot", script: "gen.js" },
        { title: "Truyện Đã Hoàn Thành", input: "/danh-sach/truyen-full", script: "gen.js" }
    ]);
}
