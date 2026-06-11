function execute() {
    return Response.success([
        {title: "今日更新", input:  "/lastupdate/", script: "gen.js"},
        {title: "上架新书", input:  "/postdate/", script: "gen.js"},
        {title: "月点击榜", input:  "/monthvisit/", script: "gen.js"},
        {title: "总收藏榜", input:  "/goodnum/", script: "gen.js"},
        {title: "新书榜单", input:  "/goodnew/", script: "gen.js"},
    ]);
}

