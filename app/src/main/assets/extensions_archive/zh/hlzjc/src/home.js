function execute() {
    return Response.success([
        { title: "近代现代", input: "/sort/1-", script: "cate.js" },
        { title: "古代架空", input: "/sort/2-", script: "cate.js" },
        { title: "穿越重生", input: "/sort/3-", script: "cate.js" },
        { title: "玄幻灵异", input: "/sort/4-", script: "cate.js" },
        { title: "网游竞技", input: "/sort/5-", script: "cate.js" },
        { title: "推理悬疑", input: "/sort/6-", script: "cate.js" },
        { title: "BL同人", input: "/sort/7-", script: "cate.js" },
        { title: "GL百合", input: "/sort/8-", script: "cate.js" },
    ]);
}