load("config.js");

// Precompile regex — dùng mỗi lần đọc chương, tránh compile lại
var EMPTY_P_RE = /<p[^>]*>\s*(<br\s*\/?>)?\s*<\/p>/gi;
var MULTI_BR_RE = /(<br\s*\/?>\s*){3,}/gi;

function execute(url) {
  var chapId = extractChapId(url);
  if (!chapId) return Response.error("URL chương không hợp lệ");

  var apiUrl = APIV2 + "/storytext?id=" + chapId;
  var res = fetch(apiUrl, FETCH_OPTIONS);
  if (res && !res.ok && !(res.status >= 400 && res.status < 500)) {
    res = fetch(apiUrl, FETCH_OPTIONS);
  }
  if (!res || !res.ok) return Response.error("Không thể tải nội dung chương");

  // Parse HTML → xóa noise → lấy body fragment sạch
  var doc = null;
  try { doc = res.html(); } catch (e) {}

  if (doc) {
    // Xóa noise tags — gộp 1 selector duy nhất, tránh 3 DOM scan riêng
    doc.select("script, style, ins, iframe, noscript, .ad, .ads, .advertisement, [class*=advert], .author-note-label, .note-label, .paid-content").remove();

    // Lấy body HTML (fragment sạch — không có <html><head> boilerplate)
    var body = doc.select("body");
    var html = body.size() > 0 ? body.get(0).html() : doc.html();

    // Xóa <p> rỗng hoặc chứa <br> thuần — vBook render thành dòng trắng thừa
    html = html.replace(EMPTY_P_RE, "");
    // Chuẩn hóa: tối đa 2 <br> liên tiếp — tránh khoảng trắng thừa do Wattpad inject
    html = html.replace(MULTI_BR_RE, "<br><br>");

    if (html && html.length > 20) return Response.success(html);
  }

  // Fallback: raw text nếu html parse thất bại
  var txt = fetchWattpadHtml(apiUrl);
  if (txt && txt.length > 20) return Response.success(txt);

  return Response.error("Không thể tải nội dung chương");
}
