load("config.js");

function execute() {
    return Response.success([
        { title: "Mới nhất", input: "newest", script: "homecontent.js" },
        { title: "Nổi bật",  input: "sort",   script: "homecontent.js" },
        { title: "Đề xuất",  input: "home",   script: "homecontent.js" },
    ]);
}