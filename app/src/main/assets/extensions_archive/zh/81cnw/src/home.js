function execute() {
    return Response.success([
        {title: "玄幻魔法", input: "/xuanhuan/", script: "cate.js"},
        {title: "都市言情", input: "/dushi/", script: "cate.js"},
        {title: "武侠修真", input: "/wuxia/", script: "cate.js"},
        {title: "历史军事", input: "/lishi/", script: "cate.js"},
        {title: "恐怖科幻", input: "/kehuan/", script: "cate.js"},
        {title: "网游同人", input: "/wangyou/", script: "cate.js"},
        {title: "完本小说", input: "/wanben/", script: "cate.js"},
    ]);
}