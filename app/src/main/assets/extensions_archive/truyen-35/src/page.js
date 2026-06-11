function execute(url) {
    let data = [];
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let size = doc.select(".pagination li").size()
        let lastPage = doc.select(".pagination li").get(size-2).text()
        for (let i = 1; i <= lastPage; i++) {
            let pageUrl = url + "?page=" + i;
            data.push(pageUrl);
        }
    }

    return Response.success(data);
}