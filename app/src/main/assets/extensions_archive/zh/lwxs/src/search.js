function execute(key) {
    let response = fetch(BASE_URL + "/search.html", {
        method: "POST",
        queries: {
            "searchkey": key,
            all: "searchtype"
        }
    });

    if (response.ok) {
        let doc = response.html();
        let books = [];
        doc.select("#alistbox > div").forEach(e => {
            let novelLink = e.select(".pic a").attr("href");
            let novelTitle = e.select(".pic a img").attr("alt");
            let author = e.select(".title span").text().replace("作者：", "");
            let chapterTitle = e.select(".sys li a").text();
            let cover = e.select(".pic img").attr("src");
            
            let description = `${author} - ${chapterTitle}`;
            
            data.push({
                name: novelTitle,
                link: novelLink,
                cover: cover,
                description: description,
                host: BASE_URL
            });
        });
        return Response.success(data);
    }
    return null;
}
