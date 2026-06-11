load('config.js');

function execute(key, page) {
    if (!page) page = '1';
    var url = BASE_URL + '/';
    var response = fetch(url, {
        queries: {
            s: key,
            paged: page
        }
    });

    if (response.ok) {
        var doc = response.html();
        var books = [];
        doc.select('.nt-grid-item, .nt-story-item, .manga-item, .item-manga, .item').forEach(function(el) {
            var nameEl = el.select('h3.nt-grid-title, h3.nt-story-title, h2.entry-title a, h3.nt-title a, .title a, a[href*="/truyen/"], a').first();
            var selfHref = el.attr('href') + "";
            var linkEl = selfHref && selfHref.indexOf('http') > -1 ? el : el.select('a[href*="/truyen/"], a').first();
            var coverEl = el.select('img').first();
            if (nameEl && linkEl) {
                var link = linkEl.attr('href') + "";
                if (!link.startsWith("http")) link = BASE_URL + (link.startsWith("/") ? "" : "/") + link;

                var cover = "";
                if (coverEl) {
                    cover = coverEl.attr('data-src') || coverEl.attr('src') || "";
                    if (cover && cover.startsWith("//")) cover = "https:" + cover;
                }

                books.push({
                    name: nameEl.text().trim(),
                    link: link,
                    cover: cover,
                    description: el.select('.entry-summary, .nt-desc-short, .nt-story-author').text().trim(),
                    host: BASE_URL
                });
            }
        });

        var next = doc.select('.page-numbers.next').size() > 0;
        return Response.success(books, next ? String(parseInt(page) + 1) : null);
    }
    return Response.error('Search failed');
}
