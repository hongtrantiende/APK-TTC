load('config.js');

function execute() {
    return Response.success([
        {title: "All Novels", input: "/novel/", script: "gen.js"},
        {title: "Latest", input: "/novel/?m_orderby=latest", script: "gen.js"},
        {title: "Trending", input: "/novel/?m_orderby=trending", script: "gen.js"},
        {title: "Most Views", input: "/novel/?m_orderby=views", script: "gen.js"},
        {title: "A-Z", input: "/novel/?m_orderby=alphabet", script: "gen.js"},
        {title: "Chinese", input: "/genre/chinese/", script: "gen.js"},
        {title: "Korean", input: "/genre/korean/", script: "gen.js"},
        {title: "Original", input: "/genre/original/", script: "gen.js"},
        {title: "GL", input: "/genre/gl/", script: "gen.js"}
    ]);
}
