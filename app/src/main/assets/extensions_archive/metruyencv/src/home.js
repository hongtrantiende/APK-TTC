function execute() {
    return Response.success([
        { title: "Cập nhậ", input: "/", script: "up.js" },
        { title: "Truyện Hot", input: "/danh-sach/truyen-hot", script: "gen.js" },
        { title: "Truyện Full", input: "/danh-sach/truyen-full", script: "gen.js" }
    ]);
}
