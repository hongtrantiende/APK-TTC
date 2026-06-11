load("config.js");

function execute(url, page) {
  if (!page) page = 0;

  let response = fetch(`${url}/${page}`);

  if (response.ok) {
    const doc = response.html();
    const list = doc.select("ul.list-group");
    const pagination = doc.select("ul.pagination");
    let next;

    if (pagination !== "") {
      let urlArr = pagination
        .select("li.active + li")
        .select("a")
        .attr("href")
        .split("/");

      next = urlArr[urlArr.length - 1];
    }

    const data = [];

    list.select(".list-group-item.list-group-item-table").forEach((e) => {
      let cover = e.select("img.img-responsive").first().attr("src");

      if (cover.startsWith("/publics")) {
        cover = BASE_URL + cover;
      }

      data.push({
        name: e.select("a.thumb").first().attr("title"),
        link: BASE_URL + e.select("a.thumb").first().attr("href"),
        cover,
        description: "",
        host: BASE_URL,
      });
    });

    return Response.success(data, next);
  }

  return null;
}
