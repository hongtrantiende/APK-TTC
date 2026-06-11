function execute(key, page) {
    var p = page || "1";
    var url = "https://truyen.codetheoyeucau.online/api/search/stories?q=" + encodeURIComponent(key) + "&limit=20&page=" + p;
    var res = fetch(url);
    if (!res.ok) return Response.error("Lỗi HTTP: " + res.status);
    
    var json = res.json();
    var list = [];
    var items = json.items || [];
    
    for (var i = 0; i < items.length; i++) {
        var item = items[i];
        list.push({
            name: item.title,
            link: "https://truyen.codetheoyeucau.online/truyen/" + item.slug,
            cover: item.coverUrl,
            description: item.authorName || item.authorSlug,
            host: "https://truyen.codetheoyeucau.online"
        });
    }
    
    var nextPage = null;
    if (items.length > 0) {
        nextPage = String(parseInt(p) + 1);
    }
    
    return Response.success(list, nextPage);
}
