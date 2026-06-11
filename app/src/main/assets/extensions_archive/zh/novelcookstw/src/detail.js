load("config.js");
function execute(url) {
    var id = parseArticleId(url);
    if (!id) return Response.error("Không đọc được articleid từ URL");
    var json = fetchApi("/novel/detail/" + id);
    if (!json || json.code !== 200) return Response.error("Tải chi tiết thất bại");
    var d = json.data, genres = [], tags = d.tag_list;
    if (tags) {
        for (var i = 0; i < tags.length; i++) {
            if (tags[i].keyword) genres.push({ title: tags[i].keyword, input: "", script: "genrecontent.js" });
        }
    }
    return Response.success({
        name: d.articlename || "", cover: coverUrl(id), host: BASE_URL,
        author: d.author || "", description: d.intro || "", detail: "",
        ongoing: d.fullflag === "0" || d.fullflag === 0,
        genres: genres, suggests: []
    });
}
