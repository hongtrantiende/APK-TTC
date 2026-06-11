load('config.js');

function execute(url) {
    url = url.replace("http://www.xiaoshubao.net", "http://m.xiaoshubao.net");
    let cvData = "";
    let part1 = url.replace(BASE_MOBILE, "").replace(BASE_MOBILE, "").replace(".html", "");
    var next = part1;
    while (next.includes(part1)) {
        let response = fetch(BASE_MOBILE + next + ".html");
        if (response.ok) {
            let doc = response.html();

            // Kiểm tra nếu trang đang chuyển mã
            if (doc.text().includes("正在为您转码……")) {
                sleep(5000); // Chờ 5 giây để load nội dung chương
                continue; // Tiếp tục vòng lặp để lấy lại nội dung chương sau khi đã chờ
            }

            next = doc.select("a:contains(下一页)").attr("href").replace(".html", "");
            let htm = doc.select("#BookText").html();
            htm = htm.replace(/\&nbsp;/g, "");
            cvData = cvData + htm;
        } else {
            break;
        }
    }
    if (cvData) {
        return Response.success(cvData);
    }
    return null;
}

function cleanHtml(htm) {
    htm = htm.replace(/(<br>\s*){2,}/g, '<br>');
    htm = htm.replace(/<a[^>]*>([^<]+)<\/a>/g, '');
    htm = htm.replace(/&(nbsp|amp|quot|lt|gt);/g, "");
    htm = htm.replace(/<!--(<br \/>)?[^>]*-->/gm, '');
    htm = htm.replace(/\&nbsp;/g, "");
    htm = htm.replace("请收藏：https://m.bqgma.com", "");
    return htm;
}

// Hàm chờ đợi trong JavaScript
function sleep(ms) {
    let start = new Date().getTime();
    let end = start;
    while (end < start + ms) {
        end = new Date().getTime();
    }
}