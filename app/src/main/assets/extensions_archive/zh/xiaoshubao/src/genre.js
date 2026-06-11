function execute() {
    return Response.success([
        { title: "奇幻玄幻", input: "/xhxs/", script: "cate.js" },
        { title: "武侠修真", input: "/xzxs/", script: "cate.js" },
        { title: "现代都市", input: "/dsxs/", script: "cate.js" },
        { title: "历史军事", input: "/lsxs/", script: "cate.js" },
        { title: "网游竞技", input: "/wyxs/", script: "cate.js" },
        { title: "科幻灵异", input: "/khxs/", script: "cate.js" },
        { title: "女频穿越", input: "/npxs/", script: "cate.js" },
        { title: "其他综合", input: "/qtxs/", script: "cate.js" },
        { title: "小说书库", input: "/xiaoshuo", script: "cate.js" },
        { title: "排行榜单", input: "/paihang/", script: "cate.js" }
    ]);
}