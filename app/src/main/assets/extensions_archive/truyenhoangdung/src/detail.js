load("config.js");

function execute(url) {
  const response = fetch(url);

  if (response.ok) {
    const doc = response.html();
    const infoHtml = doc.select("#chi_tiet .info");
    const name = infoHtml.select(".title").first();
    name.select("span").remove();

    let cover = doc.select("#chi_tiet .image-cover img").first().attr("src");

    if (cover.startsWith("/publics")) {
      cover = BASE_URL + cover;
    }

    const author = infoHtml
      .select(".list .item")
      .get(1)
      .select(".item-value")
      .text();
    const des = doc.select("#noidung").html();
    const status = infoHtml
      .select(".list .item")
      .get(3)
      .select(".item-value")
      .text();
    const detail = `Tên gốc: ${infoHtml
      .select(".list .item")
      .get(0)
      .select(".item-value")
      .text()}
      <br />
      Tác giả: ${author}
      <br />
      Trạng thái: ${status}`;

    return Response.success({
      name: name.text(),
      cover,
      author: author || "Unknow",
      description: des,
      detail,
      ongoing: status === " Hoàn Thành",
      host: BASE_URL,
    });
  }

  return null;
}
