load("config.js");

function execute(url) {
  let response = fetch(url);

  if (response.ok) {
    const doc = response.html();
    let list = doc.select("div#chuong .list-chap .row a");
    const data = [];

    for (let i = list.size() - 1; i >= 0; i--) {
      let e = list.get(i);

      data.push({
        name: e.text().replace(e.select("span").text(), "").trim(),
        url: BASE_URL + e.attr("href"),
        host: BASE_URL,
      });
    }
    return Response.success(data);
  }

  return null;
}
