function execute() {
    return Response.success([
        { title: "玄幻", input: "/sort/1_", script: "gen.js" },
        { title: "武侠", input: "/sort/2_", script: "gen.js" },
        { title: "都市", input: "/sort/3_", script: "gen.js" },
        { title: "历史", input: "/sort/4_", script: "gen.js" },
        { title: "科幻", input: "/sort/5_", script: "gen.js" },
        { title: "游戏", input: "/sort/6_", script: "gen.js" },
        { title: "女生", input: "/sort/9_", script: "gen.js" },
        { title: "其他", input: "/sort/10_", script: "gen.js" },
    ]);
}
