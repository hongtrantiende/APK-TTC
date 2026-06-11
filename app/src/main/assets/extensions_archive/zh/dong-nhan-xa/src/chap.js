function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('gb2312');
        let htm = doc.select(".read_chapterName h1").text() + doc.select(".read_chapterDetail").html();
        return Response.success(htm);
    }
    return null;
}