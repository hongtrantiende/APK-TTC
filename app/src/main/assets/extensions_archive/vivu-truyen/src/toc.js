load('config.js');

function getBookSlug(url) {
    if (!url) return '';
    // 1. Remove protocol if exists
    var clean = url.replace(/^(https?:)?\/\//i, '');
    // 2. If it contains a slash, and the part before the first slash has a dot or colon, it is a host part
    var firstSlashIdx = clean.indexOf('/');
    if (firstSlashIdx !== -1) {
        var hostPart = clean.substring(0, firstSlashIdx);
        if (hostPart.indexOf('.') !== -1 || hostPart.indexOf(':') !== -1) {
            clean = clean.substring(firstSlashIdx);
        }
    }
    // 3. Remove query parameters and hash fragments
    clean = clean.split('?')[0].split('#')[0];
    // 4. Strip leading and trailing slashes
    clean = clean.replace(/^\/+|\/+$/g, '');
    return clean;
}

function extractChapterNumber(name) {
    var match = name.match(/Chương\s*(\d+)/i);
    if (match) return parseInt(match[1], 10);
    return null;
}

function execute(link) {
    var response = fetchPage(link);
    if (!response.ok) {
        return Response.error('HTTP Error: ' + response.status);
    }

    var doc = response.html();
    var data = [];
    var seen = {};
    
    var bookSlug = getBookSlug(link).toLowerCase();
    if (!bookSlug) {
        return Response.error('Invalid Book URL');
    }

    try {
        var chapters = doc.select('a.chap-title, .list a.chap-title, ul.uk-switcher a[href*="/chuong-"], a[href*="/chuong-"]');

        for (var i = 0; i < chapters.size(); i++) {
            var chapter = chapters.get(i);
            var id = chapter.attr('id') || '';
            if (id === 'first_chap') continue;

            var href = chapter.attr('href') || '';
            var text = (chapter.text() || '').replace(/\s+/g, ' ').trim();

            if (!href || !text) continue;
            
            var chapSlug = getBookSlug(href).toLowerCase();
            if (chapSlug.indexOf(bookSlug + '/') !== 0) continue;

            var absoluteUrl = href;
            if (absoluteUrl.indexOf('http') !== 0) {
                absoluteUrl = absoluteUrl.charAt(0) === '/' ? BASE_URL + absoluteUrl : BASE_URL + '/' + absoluteUrl;
            }
            
            var normalizedHref = absoluteUrl.replace(/\/$/, '').toLowerCase();
            if (seen[normalizedHref]) continue;

            seen[normalizedHref] = true;
            data.push({ name: text, url: absoluteUrl, host: BASE_URL });
        }
        
        if (data.length > 1) {
            var firstNum = extractChapterNumber(data[0].name);
            var lastNum = extractChapterNumber(data[data.length - 1].name);
            if (firstNum !== null && lastNum !== null && firstNum > lastNum) {
                data = data.reverse();
            }
        }
        
    } catch (err) {
    }
    
    return Response.success(data);
}

