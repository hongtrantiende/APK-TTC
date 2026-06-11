function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('gb2312');
        let coverImg = doc.select("div.pic img").attr("src");
        let author =  doc.select("div.infos div.date span a").first().text();
        //let detail = doc.select(".row.g-3.small .col-md-12.mt-3.d-flex.flex-wrap").text();
        //detail = Html.clean(detail, ["p"]);
        let description = doc.select("div.infos p").html();
        //let ongoing = doc.select(".mb-3.d-flex.flex-wrap.gap-2.align-items-center span").text()
        return Response.success({
            name: doc.select("div.infos h1").text(),
            cover: coverImg,
            author: author,
            description: description,
            //detail: detail,
            //ongoing: ongoing,
            host: "https://tongrenshe.cc"
        });
    }
    return null;
}