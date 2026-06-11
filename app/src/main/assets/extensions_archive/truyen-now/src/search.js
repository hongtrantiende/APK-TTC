load("config.js");

function execute(key, page) {
  if (!page) page = "1";
  const html = fetch(BASE_URL + "/danh-sach", {
    queries: {
      q: key,
      page: page,
    },
  }).html();

  const list = html.select("#list-view .story-item").map((e) => {
    return {
      name: e.select(".story-name").text(),
      link: BASE_URL + e.select(".story-name").attr("href"),
      cover: BASE_URL + e.select("img").first().attr("src"),
      description: e.select(".story-desc").text(),
      host: BASE_URL,
    };
  });

  let next = html.select("a[title='Trang sau']").attr("href").match(/\d+$/);
  if (next != null) {
    next = next[0];
  }

  return Response.success(list, next);
}
