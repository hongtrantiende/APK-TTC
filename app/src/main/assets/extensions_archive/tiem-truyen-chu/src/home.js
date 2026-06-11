load("config.js");

function execute() {
    return Response.success([
        { title: "Mới cập nhật", input: "api://stories?sort=updated", script: "gen.js" },
        { title: "Truyện mới", input: "api://stories?sort=new", script: "gen.js" },
        { title: "Truyện Full", input: "api://stories?status=full", script: "gen.js" },
        { title: "Tất Cả", input: "api://stories?sort=views", script: "gen.js" },
        { title: "Truyện Nam", input: "api://stories?gender=nam", script: "gen.js" },
        { title: "Truyện Nữ", input: "api://stories?gender=nu", script: "gen.js" },
        { title: "BXH - Lượt xem", input: "api://ranking?by=views", script: "gen.js" },
        { title: "BXH - Quà tặng", input: "api://ranking?by=gift", script: "gen.js" },
        { title: "BXH - Đề cử", input: "api://ranking?by=nominations", script: "gen.js" },
        { title: "BXH - Theo dõi", input: "api://ranking?by=follows", script: "gen.js" },
        { title: "BXH - Bình luận", input: "api://ranking?by=comments", script: "gen.js" }
    ]);
}