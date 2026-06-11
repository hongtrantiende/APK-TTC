load("config.js");

var TOC_FIELDS = { fields: "id,parts(id,title,url)" };

function execute(url) {
  var storyId = extractStoryId(url);
  if (!storyId) return Response.error("URL không hợp lệ");

  var data = fetchWattpadJson(API_V4 + "/stories/" + storyId, TOC_FIELDS);
  // Fallback sang v3 nếu v4 thất bại hoặc không trả về parts
  if (!data || !data.parts) {
    data = fetchWattpadJson(API_V3 + "/stories/" + storyId, TOC_FIELDS);
  }
  if (!data || !data.parts || !data.parts.length) return Response.success([]);

  var list = [];
  var parts = data.parts;
  // Dùng for loop thay forEach — an toàn hơn với Rhino JS engine
  for (var i = 0; i < parts.length; i++) {
    var v = parts[i];
    if (!v.url) continue;
    list.push({ name: v.title || ("Chương " + (i + 1)), url: v.url });
  }
  return Response.success(list);
}
