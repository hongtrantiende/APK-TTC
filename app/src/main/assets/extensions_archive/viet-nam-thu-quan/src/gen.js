load("config.js");

function execute(input, page) {
    let pageNum = parseInt(page || "1");
    if (!pageNum || pageNum < 1) pageNum = 1;

    let url = input || BASE_URL + "/Truyen/moinhat/";
    if (pageNum > 1) {
        url = url.replace(/\/+$/, "") + "/Trang-" + pageNum;
    }
    let doc = fetchDoc(url);
    if (!doc) return Response.success([]);

    let list = parseNovelList(doc);
    let next = "";
    if (list.length > 0) {
        let pages = doc.select("a.page-number, a.page-btn");
        let maxPage = 1;
        pages.forEach(a => {
            let m = ("" + a.attr("href")).match(/Trang-(\d+)/);
            if (m) {
                let n = parseInt(m[1], 10);
                if (n > maxPage) maxPage = n;
            }
        });
        if (pageNum < maxPage) next = (pageNum + 1) + "";
    }

    return Response.success(list, next);
}