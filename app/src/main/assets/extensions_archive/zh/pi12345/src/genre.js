function execute() {
    return Response.success([
        { title: "全部", input: "/category/all/", script: "cate.js" },
        { title: "玄幻魔幻", input: "/category/1/", script: "cate.js" },
        { title: "武侠仙侠", input: "/category/2/", script: "cate.js" },
        { title: "都市言情", input: "/category/3/", script: "cate.js" },
        { title: "历史军事", input: "/category/4/", script: "cate.js" },
        { title: "网游竞技", input: "/category/5/", script: "cate.js" },
        { title: "科幻灵异", input: "/category/6/", script: "cate.js" },
        { title: "其它类型", input: "/category/7/", script: "cate.js" },
        { title: "女生频道", input: "/category/8/", script: "cate.js" },
    ]);
}
