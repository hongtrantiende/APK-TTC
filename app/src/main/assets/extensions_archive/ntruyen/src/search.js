load('config.js');
function execute(key, page) {
    var q = encodeURIComponent(key);
    var target = BASE_URL + '/tim-kiem?keyword=' + q;
    if (page && page !== '1') target += '&page=' + page;
    var response = fetchPage(target);
    if (response.ok) {
        var doc = response.html();
        var next = null;
        var nextHref = doc.select("a[aria-label='Go to next page']").attr('href');
        if (nextHref) {
            var match = nextHref.match(/page=(\d+)/);
            if (match) next = match[1];
        }
        var el = doc.select("a[itemprop='hasPart']");
        var data = [];
        el.forEach(function(e) {
            data.push({
                name: e.select("p[itemprop='name']").text(),
                link: e.attr('href'),
                cover: e.select("img[itemprop='image']").attr('data-src') || e.select("img[itemprop='image']").attr('src'),
                description: [
                    e.select("[itemprop='author'] [itemprop='name']").text(),
                    e.select("[itemprop='bookFormat']").text(),
                    e.select("[itemprop='dateModified']").text(),
                    e.select("[itemprop='genre']").text()
                ].filter(function(t) { return t && t.length > 0; }).join(' - '),
                host: BASE_URL
            });
        });
        return Response.success(data, next);
    }
    return Response.error('HTTP Error: ' + response.status);
}