function execute() {
    return Response.success([
        {title: "同人小说", input:  "https://www.qbtr.cc/tongren/", script: "gen.js"},
        {title: "常规小说", input:  "https://www.qbtr.cc/changgui/", script: "gen.js"},
        {title: "热门排行", input:  "https://www.qbtr.cc/hot/", script: "gen.js"},
    ]);
}