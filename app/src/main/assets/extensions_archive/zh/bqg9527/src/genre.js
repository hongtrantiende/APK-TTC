function execute() {
    return Response.success([
        { title: "玄幻", input: "/sort/1/", script: "cate.js" },
        { title: "修真", input: "/sort/2/", script: "cate.js" },
        { title: "都市", input: "/sort/3/", script: "cate.js" },
        { title: "历史", input: "/sort/4/", script: "cate.js" },
        { title: "网游", input: "/sort/5/", script: "cate.js" },
        { title: "科幻", input: "/sort/6/", script: "cate.js" },
        { title: "恐怖", input: "/sort/7/", script: "cate.js" },
        { title: "其他", input: "/sort/8/", script: "cate.js" },
    ]);
}