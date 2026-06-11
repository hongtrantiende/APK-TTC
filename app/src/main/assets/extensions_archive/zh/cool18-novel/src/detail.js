load("config.js");
function execute(url) {
  let response = fetch(url);
  if (response.ok) {
    let doc = response.html();
    Console.log(doc);
    let data = {
      name: doc.select("h1.main-title").text(),
      cover: null,
      author: doc.select("span.sender a").text(),
      description: "Không có mô tả",
      detail: "Không có chi tiết",
      ongoing: false,
      host: BASE_URL,
    };
    return Response.success(data);
  }
  return null;
}
