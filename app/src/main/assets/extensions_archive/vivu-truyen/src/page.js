load('config.js');

function execute(url) {
    if (!url) return Response.error('URL không hợp lệ');

    var targetUrl = url;
    if (targetUrl.indexOf('http') !== 0) {
        targetUrl = targetUrl.charAt(0) === '/' ? BASE_URL + targetUrl : BASE_URL + '/' + targetUrl;
    }

    // Vivu Truyện không phân trang danh sách chương (TOC), trả về URL của truyện dưới dạng danh sách trang mục lục
    return Response.success([targetUrl]);
}
