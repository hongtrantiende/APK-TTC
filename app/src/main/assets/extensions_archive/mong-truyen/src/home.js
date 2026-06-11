function execute() {
    return Response.success([
        { title: "Truyện Hoàn Thành", input: "https://mongtruyen.com/hoan-thanh.html", script: "gen.js" },
        { title: "Truyện Chờ Full", input: "https://mongtruyen.com/truyen-cho-full.html", script: "gen.js" }
    ]);
}