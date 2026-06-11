load("config.js");

function execute(url) {
    let response = fetch(url);
    if (!response || !response.ok) {
        return Response.error("Không thể tải trang");
    }
    
    let doc = response.html();
    
    // Extract chapter content from article tag
    let contentElement = doc.select("#article").first();
    if (!contentElement) {
        // Fallback selectors for different page structures
        const fallbackSelectors = [
            "article",
            ".chapter-content", 
            "#content",
            ".content",
            "#bookcontent",
            "#chaptercontent",
            ".article-content",
            ".text-content"
        ];
        
        for (let selector of fallbackSelectors) {
            contentElement = doc.select(selector).first();
            if (contentElement && contentElement.html().trim().length > 0) {
                break;
            }
        }
    }
    
    if (!contentElement) {
        return Response.error("Không tìm thấy nội dung chương");
    }
    
    let content = contentElement.html();
    
    // Clean up content
    content = cleanChapterContent(content);
    
    if (!content || content.trim() === "" || content === "[]") {
        return Response.error("Nội dung chương trống");
    }
    
    return Response.success(content);
}

function cleanChapterContent(content) {
    if (!content) return "";
    
    // Remove advertisements and unwanted elements
    content = content.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '');
    content = content.replace(/<ins[^>]*>[\s\S]*?<\/ins>/gi, '');
    content = content.replace(/<!--[\s\S]*?-->/g, '');
    
    // Remove ad-related patterns
    content = content.replace(/\(AdProvider[\s\S]*?\);/gi, '');
    content = content.replace(/https:\s*\/\/A\.\s*magsrv\.\s*com\/ad-provider\.\s*js/gi, '');
    
    // Clean up excessive line breaks
    content = content.replace(/(<br\s*\/?>){3,}/gi, '<br><br>');
    content = content.replace(/\s*<br\s*\/?>\s*<br\s*\/?>\s*/gi, '<br><br>');
    
    // Remove empty paragraphs
    content = content.replace(/<p[^>]*>\s*<\/p>/gi, '');
    
    return content.trim();
}
