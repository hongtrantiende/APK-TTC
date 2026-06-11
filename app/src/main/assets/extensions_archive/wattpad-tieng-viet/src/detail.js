load("config.js");

var DETAIL_FIELDS = { fields: "id,title,cover,url,description,user(name),completed,categories,tags" }; // cache

function execute(url) {
  var match = extractStoryId(url);
  if (!match) return Response.error("URL không hợp lệ");
  var storyId = match;

  var data = fetchWattpadJson(API_V4 + "/stories/" + storyId, DETAIL_FIELDS);
  // Fallback sang v3 nếu v4 thất bại hoặc không trả về title
  if (!data || !data.title) {
    data = fetchWattpadJson(API_V3 + "/stories/" + storyId, DETAIL_FIELDS);
  }
  if (!data || !data.title) return Response.error("Không thể tải thông tin truyện");

  var genreStr = "";
  var firstTag = null;
  var firstGenre = null;
  var genres = [];
  if (data.categories) {
    var cats = data.categories;
    for (var ci = 0; ci < cats.length; ci++) {
      var c = cats[ci];
      var cname = c.viName || c.name || c.enName;
      if (cname) {
        if (!firstGenre) firstGenre = cname;
        if (genreStr) genreStr += ", ";
        genreStr += cname;
        genres.push({ title: cname, input: cname, script: "genrecontent.js" });
      }
    }
  }
  if (data.tags) {
    var tags = data.tags;
    for (var ti = 0; ti < tags.length; ti++) {
      var t = tags[ti];
      if (!firstTag) firstTag = t;
      if (genreStr) genreStr += ", ";
      genreStr += t;
    }
  }

  var suggests = [];
  if (firstTag) {
    suggests.push({ title: "Truyện cùng thể loại", input: firstTag, script: "suggest.js" });
  } else if (firstGenre) {
    suggests.push({ title: "Truyện cùng thể loại", input: firstGenre, script: "suggest.js" });
  }
  if (data.user && data.user.name) {
    suggests.push({ title: "Truyện cùng tác giả", input: "author:" + data.user.name, script: "suggest.js" });
  }

  return Response.success({
    name: data.title,
    cover: data.cover || "",
    host: BASE_URL,
    author: data.user ? data.user.name : "",
    description: data.description || "",
    detail: genreStr,
    ongoing: !data.completed,
    genres: genres,
    suggests: suggests,
  });
}
