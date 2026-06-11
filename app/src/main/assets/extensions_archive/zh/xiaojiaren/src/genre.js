function execute() {
    return Response.success([
        { title: "全部", input: "/class/cat/", script: "cate.js" },
        { title: "悬疑", input: "/class/cat/5", script: "cate.js" },
        { title: "言情", input: "/class/cat/7", script: "cate.js" },
        { title: "都市", input: "/class/cat/3", script: "cate.js" },
        { title: "仙武", input: "/class/cat/2", script: "cate.js" },
        { title: "历史", input: "/class/cat/4", script: "cate.js" },
        { title: "游戏", input: "/class/cat/6", script: "cate.js" },
        { title: "科幻", input: "/class/cat/9", script: "cate.js" },
        { title: "玄幻", input: "/class/cat/1", script: "cate.js" },
        { title: "其他", input: "/class/cat/8", script: "cate.js" },
    ]);
}