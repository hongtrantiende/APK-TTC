load('config.js');

function execute(url) {
    var response = fetchPage(url);
    if (!response.ok) {
        return Response.error('HTTP Error: ' + response.status);
    }

    var doc = response.html();

    // Priority 1: Try standard content selectors
    var contentEl = doc.select('#read-content').first();
    if (!contentEl || !contentEl.html() || contentEl.html().length < 100) {
        contentEl = doc.select('#chapter-content').first();
    }

    if (contentEl && contentEl.html() && contentEl.html().length >= 100) {
        contentEl.select('.slide').remove();
        var data = cleanHtml(contentEl.html());
        return Response.success(data);
    }

    // Priority 2: Try Next.js stream extraction
    var nextData = extractNextContent(doc);
    if (nextData) {
        return Response.success(cleanHtml(nextData));
    }

    return Response.error('Không lấy được nội dung chương. Vui lòng kiểm tra trang nguồn.');
}

function extractNextContent(doc) {
    var content = '';
    var regex = /self\.__next_f\.push\(\[1,"((?:\\.|[^"\\])*)"\]\)/g;

    doc.select('script').forEach(function(e) {
        var script = e.html();
        if (!script || script.indexOf('__next_f') === -1) return;

        var match;
        while ((match = regex.exec(script)) !== null) {
            var raw = match[1];
            // Check if this chunk contains paragraph or line break content
            if (raw.indexOf('\\u003cbr\\u003e') === -1 && raw.indexOf('<br>') === -1 && raw.indexOf('\\u003cp') === -1) continue;

            var decoded;
            try {
                decoded = JSON.parse('"' + raw + '"');
            } catch (err) {
                continue;
            }
            if (decoded.length > content.length) {
                content = decoded;
            }
        }
    });

    return content;
}

function cleanHtml(htm) {
    if (!htm) return '';
    return htm
        .replace(/·/g, '')
        .replace(/&nbsp;/gi, ' ')
        .replace(/<p>\s*(?:&nbsp;)?\s*<\/p>/gi, '')
        .replace(/<\/p>\s*<p[^>]*>/gi, '<br>')
        .replace(/<script[\s\S]*?<\/script>/gi, '')
        .replace(/<style[\s\S]*?<\/style>/gi, '')
        .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '')
        .trim();
}