load("config.js");

function execute(url) {
    let reqUrl = url;
    if (url.indexOf("?") !== -1) {
        reqUrl = url.substring(0, url.indexOf("?"));
    }

    let response = fetch(reqUrl);
    if (!response.ok) return Response.success([reqUrl]);
    let html = response.text();

    let maxPage = 1;
    let currentUrl = reqUrl;
    let page = 1;

    // Quét tìm max page thực tập (Xóa bỏ giới hạn hiển thị 10 trang của web)
    while (page <= 50) {
        let doc = fetch(currentUrl + "?P=" + page).html();
        if (!doc) break;

        let paginationLinks = doc.select(".pager a, .pagination a, .page-item a");
        let hasNext = false;

        for (let i = 0; i < paginationLinks.size(); i++) {
            let aTag = paginationLinks.get(i);
            let href = aTag.attr("href");
            let text = aTag.text();

            if (href) {
                let match = href.match(/(?:P|p|page)=(\d+)/);
                if (match) {
                    let p = parseInt(match[1]);
                    if (p > maxPage) maxPage = p;
                    // Phím tắt: Nếu trang web có chữ "Cuối" thì đây là trang cuối chuẩn
                    if (text && text.indexOf("Cuối") !== -1) {
                        maxPage = p;
                        hasNext = false; // Ngừng vòng lặp vì đã tìm thấy trang cuối cứng
                        break;
                    }
                }
            }
        }

        // Kiểm tra xem có cần lật trang để tìm thêm maxPage không 
        // Vd nếu trang hiện tại là 1, maxPage hiển thị là 10, ta dịch loop sang trang 10 để xem nó có hiện nút sang trang 11 không
        if (maxPage > page && maxPage < 100) {
            page = maxPage;
            hasNext = true;
        } else {
            hasNext = false;
        }

        if (!hasNext) break;
    }

    let list = [];
    for (let i = 1; i <= maxPage; i++) {
        list.push(reqUrl + "?P=" + i);
    }
    return Response.success(list);
}
