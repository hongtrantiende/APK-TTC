load('config.js');

function execute() {
    return Response.success([
        {title: "男生小说", input: "/nansheng/", script: "gen.js"},
        {title: "女生言情", input: "/yanqing/", script: "gen.js"},
        {title: "耽美同人", input: "/tongren/", script: "gen.js"},
        {title: "都市小说", input: "/dushi/", script: "gen.js"},
        {title: "玄幻奇幻", input: "/xuanhuan/", script: "gen.js"},
        {title: "武侠修真", input: "/xiuzhen/", script: "gen.js"},
        {title: "网游竞技", input: "/wangyou/", script: "gen.js"},
        {title: "历史军事", input: "/lishi/", script: "gen.js"},
        {title: "科幻灵异", input: "/kehuan/", script: "gen.js"},
        {title: "其他小说", input: "/qita/", script: "gen.js"}
    ]);
}
