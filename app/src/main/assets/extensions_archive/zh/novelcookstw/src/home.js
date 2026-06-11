function execute() {
    return Response.success([
        { title: "Nổi Bật",               input: "/novel/hot", script: "homecontent.js" },
        { title: "Tiểu thuyết nổi tiếng", input: "rank",       script: "rankcontent.js" }
    ]);
}
