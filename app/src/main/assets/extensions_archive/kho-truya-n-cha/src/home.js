function execute() {
    return Response.success([
        { title: "Truyện Hot", input: "/stories/hot?page=1&limit=24", script: "gen.js" },
        { title: "Mới cập nhật", input: "/stories/latest?page=1&limit=24", script: "gen.js" }
    ]);
}
