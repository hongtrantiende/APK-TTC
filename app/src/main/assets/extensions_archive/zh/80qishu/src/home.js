load('config.js');

function execute() {
    return Response.success([
        {title: "最新", input: "/new/", script: "gen.js"},
        {title: "书库", input: "/all/", script: "gen.js"},
        {title: "排行", input: "/hot/", script: "gen.js"},
        {title: "推荐", input: "/recommendall/", script: "gen.js"},
        {title: "男生小说", input: "/nansheng/", script: "gen.js"},
        {title: "女生言情", input: "/yanqing/", script: "gen.js"},
        {title: "玄幻奇幻", input: "/xuanhuan/", script: "gen.js"},
        {title: "都市小说", input: "/dushi/", script: "gen.js"},
        {title: "历史军事", input: "/lishi/", script: "gen.js"},
        {title: "科幻灵异", input: "/kehuan/", script: "gen.js"}
    ]);
}
