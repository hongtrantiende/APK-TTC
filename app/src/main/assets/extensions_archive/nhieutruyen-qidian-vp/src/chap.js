function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let scriptTags = doc.select("script");
        let contentFragments = [];

        for (let i = 0; i < scriptTags.size(); i++) {
            let script = scriptTags.get(i).html();
            if (script && script.indexOf('self.__next_f.push') !== -1) {
                let regex = /self\.__next_f\.push\(\[1,"([\s\S]*?)"\]\)/g;
                let match;
                while ((match = regex.exec(script)) !== null) {
                    let fragment = match[1];
                    try {
                        fragment = JSON.parse('"' + fragment + '"');
                    } catch (e) {}
                    contentFragments.push(fragment);
                }
            }
        }

        if (contentFragments.length === 0) {
            let htm = doc.select("#chapter-content");
            if (htm) {
                htm.select("h2").remove();
                return Response.success(cleanHtml(htm.html()));
            }
            return null;
        }

        function unescape(str) {
            return str
                .replace(/\\n/g, '\n')
                .replace(/\\r/g, '\r')
                .replace(/\\"/g, '"')
                .replace(/\\\\/g, '\\');
        }

        let fullContent = '';
        let seenPrefixes = {};
        for (let j = 0; j < contentFragments.length; j++) {
            let frag = unescape(contentFragments[j]);
            
            // Marker strip: Key (28:), Marker (T30a6,)
            let cleaned = frag.replace(/^[a-z0-9]+:(T[a-z0-9]+,)?/i, '').trim();
            
            // Skip structural JSON/Array fragments
            if (cleaned.length < 10 || cleaned.charAt(0) === '{' || cleaned.charAt(0) === '[' || cleaned.charAt(0) === '$') {
                continue;
            }

            // Skip obvious technical file chunks
            if (cleaned.indexOf('static/chunks') !== -1 || cleaned.indexOf('initialTree') !== -1) {
                continue;
            }

            // Deduplicate (skip if prefix already seen)
            let checkPrefix = cleaned.substring(0, 100).replace(/\s+/g, ' ').trim();
            if (checkPrefix.length > 5 && seenPrefixes[checkPrefix]) continue;
            seenPrefixes[checkPrefix] = true;

            fullContent += cleaned + "\n";
        }

        if (!fullContent) return null;

        // Ensure we starting from the first actual "Chương " title
        let slicePoint = fullContent.indexOf('Chương ');
        if (slicePoint !== -1) {
            fullContent = fullContent.substring(slicePoint);
        }

        // Remove trailing advertisements/recommendations
        let adMarkers = ['Đề cử một bản bằng hữu sách mới', 'Chữ sai ra tay trước sau đổi'];
        for (let k = 0; k < adMarkers.length; k++) {
            let pos = fullContent.indexOf(adMarkers[k]);
            if (pos !== -1 && pos > 500) { // Safety: only at the end
                fullContent = fullContent.substring(0, pos).trim();
            }
        }

        // Strip "Recommended" section if it leaked in at the end
        let endMatch = fullContent.indexOf('Đề cử một bản bằng hữu sách mới');
        if (endMatch !== -1) {
            fullContent = fullContent.substring(0, endMatch);
        }

        return Response.success(cleanHtml(fullContent));
    }
    return null;
}
function cleanHtml(htm) {
  return htm
    .replace(/\\n/g, '\n')
    .replace(/\\"/g, '"')
    .replace(/\\r/g, '')
    .replace(/·/g, '')
    .replace(/&nbsp;/gi, '')
    .replace(/<p>\s*(?:&nbsp;)?\s*<\/p>/gi, '')
    .replace(/<\/p>\s*<p[^>]*>/gi, '<br>')
    .replace(/<p[^>]*>/gi, '')
    .replace(/<\/p>/gi, '<br>')
    .replace(/<div[^>]*>/gi, '')     // <== Thêm dòng này để xóa thẻ <div>
    .replace(/<\/div>/gi, '')        // <== Thêm dòng này để xóa thẻ </div>
    .replace(/\s*style="[^"]*"/g, '')
    .replace(/\r?\n+/g, '<br>')
    .replace(/(<br>\s*)+/gi, '<br>')
    .replace(/\b((?:[\u00C0-\u1EF9a-zA-Z]{1}\.){2,}[\u00C0-\u1EF9a-zA-Z]{1})\b/g, function (s) {
      return s.replace(/\./g, '');
    });
}

