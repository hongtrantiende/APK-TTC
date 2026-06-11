load('config.js');
function execute(key, page) {
    if (!page) page = '0';
    let response = fetch(BASE_URL + "/tim-kiem?q=" + key + "&qs=1" + "&start=" + page + "&vo=1", {method:"GET", headers: {"User-Agent":"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36","Referer": BASE_URL + "/","Origin": BASE_URL,"Accept":"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8","Accept-Language":"vi-VN,vi;q=0.9,en;q=0.8"}});

    if (response.ok) {
        let doc = response.html();
        let next = doc.select(".pagination").select("li.active + li").select("a").attr("href").match(/start=(\d+)/)
        if (next) next = next[1]
        let data = [];
        doc.select(".book-list > .book-item").forEach(e => {
            data.push({
                name: e.select(".book-title").text(),
                link: e.select(".info-col > a").first().attr("href"),
                cover: e.select(".cover-col img").attr("src"),
                description: e.select(".book-author").text(),
                host: BASE_URL
            });
        });

        return Response.success(data, next);
    }

    return null;
}