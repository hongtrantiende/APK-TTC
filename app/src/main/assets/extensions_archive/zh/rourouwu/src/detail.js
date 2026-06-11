function execute(url) {
    url = url.replace("www.rourouwu.com", "wap.rourouwu.com");

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let detail = "";
        doc.select(".article_info_td > div").forEach(e => {
            detail += e.text() + "<br>"
        });
        return Response.success({
            name: doc.select(".h_nav_items").select("li").last().text(),
            cover: doc.select("img").attr("src"),
            author: doc.select("div").get(0).text().replace("作者：", ""),
            description: doc.select("#novelMain").select("div").get(2).select("div").html(),
            detail: detail,
            host: "https://wap.rourouwu.com"
        });
    }
    return null;
}