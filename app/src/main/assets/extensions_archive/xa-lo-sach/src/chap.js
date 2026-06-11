load("config.js");

let API_BASE = "https://5xu.g5manhua.com";

function pickContentElement(doc) {
  let el = doc.select("#lst_content").first();
  if (el == null) el = doc.select("#chapcontent").first();
  if (el == null) el = doc.select(".chapcontent").first();
  if (el == null) el = doc.select("#content_chap").first();
  return el;
}

function extractChapterId(html) {
  let match = /baoloitruyen\('([0-9a-f]{24})'/i.exec(html);
  if (match && match.length > 1) return match[1];

  match = /'([0-9a-f]{24})','-1'\)/i.exec(html);
  if (match && match.length > 1) return match[1];

  return null;
}

function isAppOnlyChapter(doc) {
  return doc.select("#android, .wrap_taiapp.bgtaiapp, #hotro").size() > 0;
}

function buildAppOnlyNotice() {
  return (
    '<hr />' +
    "<p><em>Ghi chu: Xalosach chi cong khai mot doan trich cua chuong nay tren web. " +
    "Plugin da thu lay qua API, nhung phan con lai khong nam trong noi dung cong khai tren web.</em></p>"
  );
}

function buildLockedNotice(chap) {
  let html =
    '<hr />' +
    "<p><strong>Chuong nay dang bi khoa tren backend cua Xalosach.</strong></p>";

  if (chap && chap.ChapContent) {
    html += "<p><em>" + chap.ChapContent + "</em></p>";
  }

  if (chap && chap.ChapPrice > 0) {
    html += "<p>Gia mo khoa trong app: " + chap.ChapPrice + " xu.</p>";
  }

  if (chap && chap.ChapPriceByCongHien > 0) {
    html +=
      "<p>Hoac mo bang cong hien: " + chap.ChapPriceByCongHien + " diem.</p>";
  }

  html +=
    "<p>Plugin khong the hien thi phan noi dung da bi khoa neu chua mo khoa trong he thong cua Xalosach.</p>";

  return html;
}

function isLockedChapter(chap) {
  if (chap == null) return false;
  if (chap.ChapStatus === 4) return true;
  if (!chap.ChapContent) return false;

  let content = chap.ChapContent.toLowerCase();
  return (
    content.indexOf("da bi khoa") >= 0 ||
    content.indexOf("mo khoa de doc") >= 0
  );
}

function fetchChapterFromApi(chapterId) {
  let response = fetch(API_BASE + "/api/tieuthuyet/getdetailchuongtieuthuyet", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      sChapterID: chapterId
    })
  });

  if (!response || !response.ok) return null;

  let json = response.json();
  if (!json || !json.success || !json.data || !json.data.currentchap) {
    return null;
  }

  return json.data.currentchap;
}

function cleanPreviewDocument(doc) {
  doc
    .select(
      "script, style, .ads, #prevchap, #nextchap, #prevchapbot, #nextchapbot, .btnmucluc, ul.navew, h1.title, .breakcumb, #loading224, #loading, #android, #hotro, #taiappfooter"
    )
    .remove();
}

function execute(url) {
  let response = fetch(url);
  if (!response || !response.ok) {
    return Response.error("Khong tai duoc trang chuong tu xalosach.");
  }

  let html = response.text();
  let doc = Html.parse(html);
  let hasAppOnlyBlock = isAppOnlyChapter(doc);

  cleanPreviewDocument(doc);

  let previewEl = pickContentElement(doc);
  let previewContent = previewEl != null ? previewEl.html() : "";
  let chapterId = extractChapterId(html);

  if (chapterId != null) {
    let apiChap = fetchChapterFromApi(chapterId);
    if (apiChap != null) {
      if (apiChap.ChapContent && !isLockedChapter(apiChap)) {
        return Response.success(apiChap.ChapContent);
      }

      if (isLockedChapter(apiChap)) {
        if (previewContent) {
          return Response.success(previewContent + buildLockedNotice(apiChap));
        }
        return Response.error(
          "Chuong nay dang bi khoa tren backend cua Xalosach, plugin khong the hien thi noi dung chua mo khoa."
        );
      }
    }
  }

  if (previewContent) {
    if (hasAppOnlyBlock) {
      previewContent += buildAppOnlyNotice();
    }
    return Response.success(previewContent);
  }

  return Response.error(
    "Khong tim thay noi dung chuong, co the do cau truc trang web da thay doi."
  );
}
