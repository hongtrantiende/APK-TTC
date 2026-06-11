load('config.js');

function execute(url) {
    let doc = fetch(url).html();
    let list = [];

    // lấy tất cả link chương (bỏ dòng "Summary")
    let chapters = doc.select(".nav-list li a[href*='/chapters/']");

    for (let i = 0; i < chapters.size(); i++) {
        let e = chapters.get(i);

        let name = e.text().trim(); // ví dụ: "1. Campaign Manager"
        let link = e.attr("href");

        if (!link) continue;

        // bỏ query ?noAutoScroll cho sạch
        link = link.split("?")[0];

        // fix link tương đối
        if (link.startsWith("/")) {
            link = BASE_URL + link;
        }

        list.push({
            name: name,
            url: link,
            host: BASE_URL
        });
    }

    return Response.success(list);
}