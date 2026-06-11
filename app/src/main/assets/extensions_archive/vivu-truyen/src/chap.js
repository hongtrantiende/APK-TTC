load('config.js');
function execute(link) {
    var response = fetchPage(link);
    if (!response.ok) {
        return Response.error('HTTP Error: ' + response.status);
    }

    var doc = response.html();
    var content = '';
    
    try {
        var contentEl = doc.select('.reading');
        if (contentEl.size() === 0) {
            contentEl = doc.select('.noi-dung');
        }

        if (contentEl.size() > 0) {
            content = contentEl.html() || '';
            content = content.replace(/<div class="google-auto-placed[\s\S]*?<\/div>/gi, '');
            content = content.replace(/<ins[\s\S]*?<\/ins>/gi, '');
            content = content.replace(/<iframe[\s\S]*?<\/iframe>/gi, '');
        }
        
    } catch (err) {
    }
    
    if (content.length === 0) {
        content = 'Không thể lấy nội dung từ trang này.';
    }
    
    return Response.success(content);
}
