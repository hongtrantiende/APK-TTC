load("config.js");

function execute(key, page) {
  if (!page) page = 0;

  let response = fetch(`${BASE_URL}/home/search/${page}`, {
    method: "GET",
    queries: { search: key },
  });

  if (response.ok) {
    const doc = response.html();
    const list = doc.select("ul.list-group");
    let next = doc
      .select("ul.pagination")
      .select("li.active + li")
      .select("a")
      .attr("href");

    if (next !== "") {
      const urlArr = next.split("/");

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
