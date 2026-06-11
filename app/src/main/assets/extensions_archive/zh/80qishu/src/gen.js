load('config.js');

function execute(url, page) {
    let target = listPageUrl(url, page);
    let doc = getDoc(target);
    if (!doc) return loadError();
    return Response.success(parseListItems(doc), nextPage(doc, target));
}
