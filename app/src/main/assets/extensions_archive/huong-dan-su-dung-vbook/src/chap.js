function execute(url) {
    var response = fetch(url);
    if (!response.ok) return Response.error("Không thể tải nội dung từ Gitbook. Vui lòng kiểm tra lại kết nối.");
    
    var text = response.text();
    if (!text) return Response.error("Nội dung chương trống");

    // Chuẩn hóa xuống dòng: Chuyển \r\n và \r đơn lẻ về \n
    var normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
    
    return renderMarkdown(normalized);
}

function renderMarkdown(md) {
    var lines = md.split('\n');
    var result = [];
    var stepCount = 1;

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i].trim();
        if (!line) {
            result.push('<br>');
            continue;
        }

        // 1. Gitbook Tags
        if (line.indexOf('{% embed') !== -1) {
            var vUrl = line.match(/url="(.*?)"/);
            if (vUrl) {
                var cleanUrl = vUrl[1].replace(/[<>\s\n\r]+/g, "").replace(/&#x26;/g, '&').trim();
                result.push('<br><center><a href=\"' + cleanUrl + '\"><u>BẤM VÀO ĐÂY ĐỂ XEM VIDEO</u></a></center><br>');
            }
            continue;
        }
        if (line.indexOf('{% step %}') !== -1) {
            result.push('<br><b style=\"color:#2e7d32;\">BƯỚC ' + (stepCount++) + '</b><br>');
            continue;
        }
        if (line.indexOf('{% tab') !== -1) {
            var tTitle = line.match(/title="(.*?)"/);
            if (tTitle) result.push('<br><b>📍 PHẦN: ' + tTitle[1] + '</b><br>');
            continue;
        }
        if (line.indexOf('{% hint') !== -1) {
            result.push('<blockquote>');
            continue;
        }
        if (line.indexOf('{% end') !== -1 || line.indexOf('{% tabs') !== -1 || line.indexOf('{% stepper') !== -1) {
            if (line.indexOf('endhint') !== -1) result.push('</blockquote>');
            if (line.indexOf('endtab') !== -1) result.push('<hr>');
            continue;
        }

        // 2. Images & Figures
        if (line.indexOf('<img') !== -1) {
            var imgUrl = line.match(/src="(.*?)"/);
            if (imgUrl) {
                var cleanImg = imgUrl[1].replace(/&#x26;/g, '&');
                result.push('<br><img src=\"' + cleanImg + '\" width=\"100%\"><br>');
            }
            continue;
        }
        if (line.indexOf('<figure') !== -1 || line.indexOf('</figure>') !== -1 || line.indexOf('<figcaption') !== -1 || line.indexOf('</figcaption>') !== -1) {
            continue;
        }

        // 3. Markdown Standard
        var processed = line;
        
        // Headers
        if (processed.indexOf('#') === 0) {
            processed = '<br><b>' + processed.replace(/#/g, '').trim() + '</b><br>';
        } else {
            // Bold, Italic, Link, Mark
            processed = processed
                .replace(/\*\*([\s\S]*?)\*\*/g, '<b>$1</b>')
                .replace(/\*([\s\S]*?)\*/g, '<i>$1</i>')
                .replace(/\[(.*?)\]\((.*?)\)/g, function(m, t, l) {
                    var cleanL = l.replace(/[<>]/g, "").trim();
                    return '<a href=\"' + cleanL + '\"><u>' + t + '</u></a>';
                })
                .replace(/<mark[\s\S]*?>/g, '<b>')
                .replace(/<\/mark>/g, '</b>');
        }

        result.push(processed + '<br>');
    }

    var finalHtml = result.join('\n');
    return Response.success('<div style=\"padding:12px;\">' + finalHtml + '</div>');
}
