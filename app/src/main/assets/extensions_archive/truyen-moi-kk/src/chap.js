function execute(url) {
  let fetchUrl = normalizeUrl(url);
  let doc = Http.get(fetchUrl).html();
  if (!doc && fetchUrl !== url) doc = Http.get(url).html();

  if (!doc) return Response.error("Khong the tai chuong");

  let contentElement = doc.select("article.chapter-content").first()
    || doc.select("#chapterBody article").first()
    || doc.select(".container.chapter article").first()
    || doc.select("#chapter-c").first()
    || doc.select(".chapter-c").first()
    || doc.select("#chapter-content").first()
    || doc.select(".chapter-content").first()
    || doc.select(".noidung").first()
    || doc.select("div[itemprop='articleBody']").first();

  if (!contentElement) {
    return Response.error("Khong tim thay noi dung chuong");
  }

  let content = contentElement.html();

  if (content) {
    content = content.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, "");
    content = content.replace(/<div[^>]*class=["'][^"']*ads[^"']*["'][^>]*>[\s\S]*?<\/div>/gi, "");
    content = content.replace(/<ins[^>]*>[\s\S]*?<\/ins>/gi, "");
    return Response.success(content);
  }

  return Response.error("HTML chuong trong");
}

function normalizeUrl(url) {
  return String(url || "").replace(/^https?:\/\/(?:www\.)?(?:truyenmoikk\.com|truyenmoiii\.org|truyenmoiyy\.com)/i, "https://truyenmoiss.com");
}
