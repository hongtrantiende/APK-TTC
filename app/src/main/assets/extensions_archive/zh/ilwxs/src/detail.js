function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('utf-8');
        let coverImg = doc.select("div#fmimg img").attr("src");
        let author =  doc.select("div#info p").get(0).text();
        let detail =  doc.select("div#info p").get(1).text();
        //detail = Html.clean(detail, ["p"]);
        let description = doc.select("div#intro").html();
        //let ongoing = doc.select(".mb-3.d-flex.flex-wrap.gap-2.align-items-center span").text()
        return Response.success({
            name: doc.select("div#info h1").text(),
            cover: coverImg,
            author: author,
            description: description,
            detail: detail,
            //ongoing: ongoing,
            host: "https://www.ilwxs.com/"
        });
    }
    return null;
}