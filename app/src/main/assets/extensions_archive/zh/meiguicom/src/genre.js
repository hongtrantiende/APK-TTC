function execute() {
    return Response.success([
        { title: "玄幻魔法", input: "/sort/1/", script: "gen.js" },
        { title: "武侠修真", input: "/sort/2/", script: "gen.js" },
        { title: "都市言情", input: "/sort/3/", script: "gen.js" },
        { title: "历史军事", input: "/sort/4/", script: "gen.js" },
        { title: "科幻灵异", input: "/sort/5/", script: "gen.js" },
        { title: "游戏竞技", input: "/sort/6/", script: "gen.js" },
        { title: "女生耽美", input: "/sort/7/", script: "gen.js" },
        { title: "其他类型", input: "/sort/8/", script: "gen.js" },
    ]);
}
