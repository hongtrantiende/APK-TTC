function execute() {
    return Response.success([
        { title: "Truyện Miễn Phí", input: "https://truyendocviet1.xyz/truyen-mien-phi.html", script: "gen.js" },
        { title: "Truyện Được Đề Cử", input: "https://truyendocviet1.xyz/truyen-duoc-de-cu.html", script: "gen.js" }
    ]);
}