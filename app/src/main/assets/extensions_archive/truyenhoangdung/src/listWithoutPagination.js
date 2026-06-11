load("config.js");

function execute(url) {
  let response = fetch(url);

  if (response.ok) {
    const doc = response.html();
    const list = doc.select("ul.list-group");
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

    return Response.success(data);
  }

  return null;
}
