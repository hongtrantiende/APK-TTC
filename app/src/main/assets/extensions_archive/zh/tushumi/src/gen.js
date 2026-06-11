function execute(url, page) {
    let url_new;
    if (!page) {
        url_new = "https://www.tushumi.cc/" + url;
    } else {
        url_new = "https://www.tushumi.cc/" + page;
    }
    const html = Http.get(url_new).html();
    let el = html.select("#alist").select("#alistbox");
    const data = []; 
    for (var i = 0; i < el.size(); i++) {
        var e = el.get(i);
        let cover = e.select("img").attr("src");
        let name = e.select(".title a").text();
        let author = e.select(".title span").text().split("作者：")[1];
        let link_info = e.select(".pic a").attr("href");
        let link = "https://www.tushumi.cc/shu/" + link_info.match(/info-(\d+)\.html/)[1] + "/";
        data.push({
            name: name,
            link: link,
            cover: cover,
            description: author,
            host: "https://www.tushumi.cc"
        })
    }
    const next = html.select(".articlepage").select(".next").attr("href");
    console.log(data.toString());
    return Response.success(data, next);
}