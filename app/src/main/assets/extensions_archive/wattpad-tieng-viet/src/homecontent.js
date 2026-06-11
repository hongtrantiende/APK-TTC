load("config.js");

function execute(url, page) {
  if (!page) page = "0";

  var data = fetchWattpadJson(url, {
    fields: "stories(id,title,url,cover,user(name)),nextUrl",
    offset: page,
    limit: "30",
  });

  if (data) return parseStories(data);
  return Response.error("Không thể tải dữ liệu");
}
