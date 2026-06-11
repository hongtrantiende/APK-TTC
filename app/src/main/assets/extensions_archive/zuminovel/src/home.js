load("config.js");

function execute() {
    return Response.success([
        { title: "Danh sách truyện", input: BASE_URL + "/list", script: "gen.js" }
    ]);
}
