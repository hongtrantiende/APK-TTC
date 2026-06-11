function execute() {
    return Response.success([
        { title: "全部", input: "/sort/", script: "cate.js" },
        { title: "玄幻奇幻", input: "/sort/1/", script: "cate.js" },
        { title: "武侠修真", input: "/sort/2/", script: "cate.js" },
        { title: "都市言情", input: "/sort/3/", script: "cate.js" },
        { title: "历史军事", input: "/sort/4/", script: "cate.js" },
        { title: "耽美女频", input: "/sort/5/", script: "cate.js" },
        { title: "游戏竞技", input: "/sort/6/", script: "cate.js" },
        { title: "科幻惊悚", input: "/sort/7/", script: "cate.js" },
        { title: "其他类型", input: "/sort/8/", script: "cate.js" },
    ]);
}