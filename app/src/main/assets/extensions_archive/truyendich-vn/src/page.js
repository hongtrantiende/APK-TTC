function execute(url) {
    if (url.slice(-1) !== "/") url = url + "/";
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let list = [];
        let infoText = doc.select(".info").text();
        let match = infoText.match(/Số chương:\s*(\d+)/);
        if (match) {
            let totalChapters = parseInt(match[1]);
            let totalPages = Math.ceil(totalChapters / 50);
            for (let i = 1; i <= totalPages; i++) {
                list.push(url + "trang-" + i + "/");
            }
        }
        return Response.success(list);
    }

    return null;
}