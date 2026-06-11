function execute() {
    return Response.success([
        { title: "首页", input: "/", script: "gen2.js" },
        { title: "排行", input: "/top", script: "gen.js" },
        { title: "全本", input: "/full/all/", script: "gen1.js" },
    ]);
}