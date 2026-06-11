load("config.js");

function execute() {
    return Response.success([
        { title: "Truyện đánh dấu", script: "bookmark.js", input: "/" },
        { title: "Top đề cử", script: "gen.js", input: "NominatedMonth" },
        { title: "Top xem nhiều", script: "gen.js", input: "HotMonth" },
        { title: "Top yêu thích", script: "gen.js", input: "Like" }
    ]);
}
