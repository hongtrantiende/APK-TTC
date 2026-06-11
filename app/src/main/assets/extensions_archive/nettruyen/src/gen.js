load('config.js');

function execute(url, page) {
    var p = parseInt(page || '1', 10);
    if (!p || p < 1) p = 1;

    var targetUrl = buildPageUrl(url, p);
    var response = fetch(targetUrl);
    if (!response || !response.ok) return null;

    var doc = response.html();
    var novelList = [];

    doc.select('.items .item > figure, .items > .item > figure').forEach(function (e) {
        var titleNode = e.select('.title-comic, figcaption h3 a').first();
        var linkNode = e.select('figcaption h3 a').first();
        var name = cleanText(titleNode ? titleNode.text() : '');
        var link = absoluteUrl(linkNode ? linkNode.attr('href') : '');

        var img = e.select('.image img').first();
        var cover = absoluteUrl((img && (img.attr('data-original') || img.attr('data-src') || img.attr('src'))) || '');

        var chapNode = e.select('ul.comic-item li.chapter a').first();
        var latestChap = cleanText(chapNode ? chapNode.text() : '');

        var liNodes = e.select('ul.comic-item li.time, ul.comic-item li');
        var latestTime = '';
        if (liNodes && liNodes.size() > 1) {
            latestTime = cleanText(liNodes.get(1).text());
        } else if (liNodes && liNodes.size() > 0) {
            latestTime = cleanText(liNodes.get(0).text());
        }
        var description = [latestChap, latestTime].filter(Boolean).join('<br>');

        if (!name || !link) return;

        novelList.push({
            cover: cover,
            name: name,
            link: link,
            description: description,
            host: BASE_URL
        });
    });

    var next = '';
    var nextAnchor = doc.select('a[rel=next], .pagination a.next').first();
    if (nextAnchor) next = String(p + 1);

    return Response.success(novelList, next);
}

function buildPageUrl(url, page) {
    var base = cleanText(url || '/tim-truyen');
    if (!/^https?:\/\//i.test(base)) {
        if (base.charAt(0) !== '/') base = '/' + base;
        base = BASE_URL + base;
    }
    var joiner = base.indexOf('?') >= 0 ? '&' : '?';
    return base + joiner + 'page=' + page;
}

function absoluteUrl(url) {
    var u = cleanText(url);
    if (!u) return '';
    if (/^https?:\/\//i.test(u)) return u;
    if (u.indexOf('//') === 0) return 'https:' + u;
    if (u.charAt(0) === '/') return BASE_URL + u;
    return BASE_URL + '/' + u;
}

function cleanText(text) {
    if (!text) return '';
    return ('' + text).replace(/\s+/g, ' ').trim();
}