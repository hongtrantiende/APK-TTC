function execute(url) {
    if(url.includes("/books/")){
        let book_id = url.match(/books-\/d+/);
       url = "https://lnovel.org/" + book_id;
    }
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('utf-8');
        let coverImg = doc.select("img.w-100.h-100").first().attr("src");
        let author =  doc.select("dd.col.text-body-tertiary").get(0).select("a").text();
        let detail = doc.select("dd.col.text-body-tertiary").get(1);
//        detail = Html.clean(detail, ["p"]);
        let description = doc.select(".my-2")+"<br>";
//        let ongoing = doc.select("dd.col").get(2).select("a").text()
        return Response.success({
            name: doc.select("h1.h3").text(),
            cover: coverImg,
            author: author,
            description: description,
            detail: detail,
//            ongoing: ongoing,
            host: "https://lnovel.org/"
        });
    }
    return null;
}