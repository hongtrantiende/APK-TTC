function execute() {
    return Response.success([
        { title: "Mới cập nhật", input: "latest", script: "gen.js" },
        { title: "Truyện HOT", input: "trending_now", script: "gen.js" },
        { title: "Tiên Hiệp", input: "/the-loai/tien-hiep", script: "gen.js" },
        { title: "Huyền Huyễn", input: "/the-loai/huyen-huyen", script: "gen.js" },
        { title: "Ngôn Tình", input: "/the-loai/ngon-tinh", script: "gen.js" }
    ]);
}
