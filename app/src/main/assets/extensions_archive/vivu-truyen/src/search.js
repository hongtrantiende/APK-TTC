load('config.js');

function normalizeUrl(url) {
    if (!url) return '';
    if (url.indexOf('http') === 0) return url;
    return url.charAt(0) === '/' ? BASE_URL + url : BASE_URL + '/' + url;
}

function execute(key, page) {
    if (!page) page = '1';
    
    var encodedKey = encodeURIComponent(key);
    var searchUrl;
    
    if (page === '1') {
        searchUrl = BASE_URL + '/?s=' + encodedKey;
    } else {
        searchUrl = BASE_URL + '/page/' + page + '/?s=' + encodedKey;
    }

    var response = fetchPage(searchUrl);
    if (!response.ok) {
        return Response.error('HTTP Error: ' + response.status);
    }

    var doc = response.html();
    var data = [];
    var seen = {};

    var items = doc.select('.uk-cover-container');
    if (items.size() > 0) {
        for (var i = 0; i < items.size(); i++) {
            var container = items.get(i);
            var linkEl = container.select('a.uk-position-cover').first();
            if (!linkEl) continue;
            var link = normalizeUrl(linkEl.attr('href') || '');
            if (!link || seen[link]) continue;
            if (link === BASE_URL + '/' || link.indexOf('/tac-gia/') > -1 || link.indexOf('/chuong-') > -1) continue;

            var title = '';
            var cover = '';
            var description = '';

            var titleEl = container.select('.de-cu-title').first();
            var chapEl = container.select('.chap-title').first();
            var chapText = chapEl ? chapEl.text().replace(/\s+/g, ' ').trim() : '';

            var statusEl = container.select('.tien-to').first();
            var statusText = statusEl ? statusEl.text().replace(/\s+/g, ' ').trim() : '';

            if (titleEl) {
                titleEl.select('.chap-title').remove();
                title = titleEl.text().replace(/\s+/g, ' ').trim();
            }

            var img = container.select('img').first();
            if (img) {
                cover = normalizeUrl(img.attr('data-src') || img.attr('src') || '');
            }

            if (statusText && chapText) {
                description = statusText + ' - ' + chapText;
            } else if (statusText) {
                description = statusText;
            } else if (chapText) {
                description = chapText;
            } else {
                description = '';
            }

            if (!title) continue;

            seen[link] = true;
            data.push({
                name: title,
                link: link,
                cover: cover,
                description: description,
                host: BASE_URL
            });
        }
    } else {
        var legacyItems = doc.select('article h3 + a[href$="/"] , article h3 ~ a[href$="/"] , article a[href$="/"]');
        for (var j = 0; j < legacyItems.size(); j++) {
            var legacyItem = legacyItems.get(j);
            var legacyLink = normalizeUrl(legacyItem.attr('href') || '');
            if (!legacyLink || seen[legacyLink]) continue;
            if (legacyLink === BASE_URL + '/' || legacyLink.indexOf('/tac-gia/') > -1 || legacyLink.indexOf('/chuong-') > -1) continue;

            var legacyContainer = legacyItem.parent();
            var legacyTitle = '';
            var legacyCover = '';
            var legacyDesc = '';

            if (legacyContainer) {
                var legacyTitleEl = legacyContainer.select('h3');
                if (legacyTitleEl.size() > 0) {
                    legacyTitle = legacyTitleEl.text().replace(/\s+/g, ' ').replace(/Chương\s*\d+.*$/i, '').trim();
                }
                var legacyImg = legacyContainer.select('img');
                if (legacyImg.size() > 0) {
                    legacyCover = normalizeUrl(legacyImg.attr('src') || legacyImg.attr('data-src') || '');
                }
                legacyDesc = (legacyContainer.text() || '').replace(/\s+/g, ' ').trim();
            }

            if (!legacyTitle) {
                legacyTitle = legacyItem.text().replace(/\s+/g, ' ').replace(/Chương\s*\d+.*$/i, '').trim();
            }
            if (!legacyTitle) continue;

            seen[legacyLink] = true;
            data.push({
                name: legacyTitle,
                link: legacyLink,
                cover: legacyCover,
                description: legacyDesc,
                host: BASE_URL
            });
        }
    }

    var next = '';
    if (doc.select('a[rel="next"], .pagination a.next, .pagination .next a').size() > 0 || doc.select('.pagination a').size() > 0) {
        next = String(parseInt(page, 10) + 1);
    }

    return Response.success(data, next);
}
