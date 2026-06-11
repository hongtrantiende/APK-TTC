function execute() {
    return Response.success([
        { title: "言情", input: "/lb/1/", script: "cate.js" },
        { title: "纯爱", input: "/lb/2/", script: "cate.js" },
        { title: "百合", input: "/lb/3/", script: "cate.js" },
        { title: "仙幻", input: "/lb/4/", script: "cate.js" },
        { title: "穿越", input: "/lb/5/", script: "cate.js" },
        { title: "悬疑", input: "/lb/6/", script: "cate.js" },
        { title: "轻小", input: "/lb/7/", script: "cate.js" },
        { title: "无C", input: "/lb/8/", script: "cate.js" },
        { title: "综合", input: "/lb//", script: "cate.js" },
        { title: "热门", input: "/rank/allvist/", script: "cate.js" },
    ]);
}