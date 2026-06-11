function execute() {
    return Response.success([
        { title: "玄幻", input: "/list/1-", script: "gen.js" },
        { title: "仙侠", input: "/list/2-", script: "gen.js" },
        { title: "言情", input: "/list/3-", script: "gen.js" },
        { title: "历史", input: "/list/4-", script: "gen.js" },
        { title: "网游", input: "/list/5-", script: "gen.js" },
        { title: "科幻", input: "/list/6-", script: "gen.js" },
        { title: "恐怖", input: "/list/7-", script: "gen.js" },
        { title: "其他", input: "/list/8-", script: "gen.js" },
        { title: "总排行榜", input: "/book/allvisit-", script: "up.js" },
        { title: "本周排行榜", input: "/book/weekvisit-", script: "up.js" },
        { title: "当月排行榜", input: "/book/monthvisit-", script: "up.js" },
        { title: "总推荐榜", input: "/book/allvote-", script: "up.js" },
        { title: "本周推荐榜", input: "/book/weekvote-", script: "up.js" },
        { title: "当月推荐榜", input: "/book/monthvote-", script: "up.js" }
    ]);
}