load("config.js");

function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        
        // Check for premium content lock
        let contentLock = doc.select(".content-lock");
        if (contentLock.text().length > 10) {
            return Response.error("Bạn cần trả phí chương này để có thể đọc.");
        }

        let allContent = "";
        let currentUrl = url;
        let pageNumber = 1;
        
        // Loop through all pages of this chapter
        while (true) {
            let currentDoc = doc;
            
            // If not the first iteration, fetch the next page
            if (pageNumber > 1) {
                let nextResponse = fetch(currentUrl);
                if (!nextResponse.ok) {
                    break;
                }
                currentDoc = nextResponse.html();
                
                // Check for premium content lock on sub-pages
                let subContentLock = currentDoc.select(".content-lock");
                if (subContentLock.text().length > 10) {
                    break;
                }
            }
            
            // Extract content from current page - try multiple selectors
            let pageContent = "";
            
            // Try different content selectors based on wfxs.tw structure
            let contentSelectors = [
                "#content",
                ".content", 
                "#bookcontent",
                ".chapter-content",
                ".text-content",
                "#chaptercontent",
                ".article-content"
            ];
            
            for (let selector of contentSelectors) {
                let element = currentDoc.select(selector).first();
                if (element && element.html().trim().length > 0) {
                    pageContent = element.html();
                    break;
                }
            }
            
            if (!pageContent || pageContent.trim() === "" || pageContent === "[]") {
                break;
            }
            
            // Add page content
            allContent += pageContent;
            if (pageNumber > 1) {
                allContent += "<br/><br/>";
            }
            
            // Check if there's a next page in this chapter
            let nextPageUrl = getNextPageUrl(currentDoc, currentUrl, pageNumber);
            if (!nextPageUrl) {
                break;
            }
            
            currentUrl = nextPageUrl;
            pageNumber++;
            
            // Safety limit
            if (pageNumber > 10) {
                break;
            }
        }
        
        return Response.success(allContent);
    }
    return null;
}

function getNextPageUrl(doc, currentUrl, currentPage) {
    // Method 1: Check navigation links for next page
    let navLinks = doc.select("a");
    
    for (let i = 0; i < navLinks.size(); i++) {
        let link = navLinks.get(i);
        let href = link.attr("href");
        let text = link.text();
        
        // Look for "下一頁" or similar text
        if (text.includes("下一頁") || text.includes("下頁") || text.includes("next") || text.includes("下一页")) {
            if (href && !href.includes("javascript") && !href.includes("#")) {
                return href.startsWith("http") ? href : BASE_URL + href;
            }
        }
    }
    
    // Method 2: URL pattern matching for wfxs.tw
    // Pattern: /xiaoshuo/1779811/83483651/1.html -> /xiaoshuo/1779811/83483651/2.html
    let match = currentUrl.match(/^(.+\/)(\d+)\.html$/);
    if (match) {
        let baseUrl = match[1];
        let currentPageNum = parseInt(match[2]);
        let nextPageNum = currentPageNum + 1;
        let nextUrl = baseUrl + nextPageNum + ".html";
        
        // Test if next page exists by making a quick check
        return nextUrl;
    }
    
    // Method 3: Handle URLs without page number (assume it's page 1, try page 2)
    if (!currentUrl.match(/\/\d+\.html$/)) {
        // If current URL doesn't end with /number.html, try adding /2.html
        let baseUrl = currentUrl.replace(/\.html$/, "");
        return baseUrl + "/2.html";
    }
    
    return null;
}
