load("config.js");
function execute(key, page) {
  if (!page) page = "1";
  let response = fetch(
    BASE_URL +
      "/index.php?action=search&bbsdr=bbs4&act=threadsearch&app=forum&keywords=" +
      encodeURIComponent(key) +
      "&submit=%E6%9F%A5%E8%AF%A2&p=" +
      page,
  );
  if (response.ok) {
    let doc = response.html();
    let data = [];
    let list = doc.select("ul.thread-list");
    if (list.size() === 0) {
      return Response.success([], null);
    }
    list.select("li.l-m1").forEach((e) => {
      data.push({
        name: e.select("a").first().text(),
        link: BASE_URL + "/" + e.select("a").first().attr("href"),
        cover: null,
      });
    });
    if (data.length === 0) {
      return Response.success([], null);
    }
    let next = (parseInt(page, 10) + 1).toString();
    return Response.success(data, next);
  }
  return null;
}
