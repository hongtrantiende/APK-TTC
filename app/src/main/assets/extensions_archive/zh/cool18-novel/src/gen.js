load("config.js");
function execute(url, page) {
  url = url.replace(
    /^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/gim,
    BASE_URL,
  );
  let response = fetch(url);
  if (response.ok) {
    let data = [];
    let doc = response.html();
    doc.select("#d_list ul.thread-list li.l-m1").forEach((e) => {
      data.push({
        name: e.select("a").first().text(),
        link: e.select("a").first().attr("href"),
        cover: null,
      });
    });
    var next = doc
      .select(".next.page-numbers")
      .first()
      .attr("href")
      .match(/page\/(\d+)/);
    if (next) next = next[1];
    else next = "";
    return Response.success(data, next);
  }
  return Response.error("Something went wrong");
}
