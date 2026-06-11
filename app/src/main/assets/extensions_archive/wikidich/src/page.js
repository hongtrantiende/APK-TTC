load('config.js');

function execute(url) {
    let story = storyUrlFromAny(url);
    let doc = getDoc(story);
    if (!doc) return Response.success([story]);

    let info = parseChapterPageInfo(doc.html ? doc.html() : "");
    if (!info.id || info.total <= 1) return Response.success([story]);

    let pages = [story];
    for (let i = 2; i <= info.total; i++) {
        pages.push(BASE_URL + "/get/listchap/" + info.id + "?page=" + i);
    }
    return Response.success(pages);
}
