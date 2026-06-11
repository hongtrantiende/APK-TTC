load("config.js");

function execute(url, page) {
    if (!page) page = '1';
    
    let allBooks = [];
    let currentPage = parseInt(page);
    let maxPagesToLoad = 5; // Load tối đa 5 trang một lần
    
    for (let i = 0; i < maxPagesToLoad; i++) {
        let pageToLoad = currentPage + i;
        
        // Build the correct URL with page number
        let pageUrl = url;
        if (pageToLoad !== 1) {
            if (url.match(/\/\d+\.html$/)) {
                pageUrl = url.replace(/\/\d+\.html$/, '/' + pageToLoad + '.html');
            } else if (url.endsWith('.html')) {
                pageUrl = url.replace(/\.html$/, '/' + pageToLoad + '.html');
            } else {
                pageUrl = url + '/' + pageToLoad + '.html';
            }
        }
        
        let response = fetch(pageUrl);
        if (!response.ok) {
            break; // Nếu page không tồn tại, dừng load
        }
        
        let doc = response.html();
        
        // Check if this is the last page
        let isLastPage = false;
        let paginationDiv = doc.select(".pages").first();
        if (paginationDiv) {
            let paginationText = paginationDiv.text();
            let match = paginationText.match(/第\s*(\d+)\s*\/\s*(\d+)\s*頁/);
            if (match) {
                let currentPageInHTML = parseInt(match[1]);
                let totalPages = parseInt(match[2]);
                if (currentPageInHTML >= totalPages) {
                    isLastPage = true;
                }
            }
        }
        
        // Extract books from current page
        let pageBooks = [];
        doc.select(".rec_rullist ul").forEach(e => {
            let titleElement = e.select(".three a").first();
            let authorElement = e.select(".five");
            let categoryElement = e.select(".two");
            
            if (titleElement && authorElement) {
                let title = titleElement.text();
                let link = titleElement.attr("href");
                let author = authorElement.text();
                let category = categoryElement.text();
                
                // Make sure link is absolute
                if (link && !link.startsWith("http")) {
                    link = BASE_URL + link;
                }
                
                pageBooks.push({
                    name: title,
                    link: link,
                    cover: "",
                    description: "作者: " + author + " | " + category + " (Trang " + pageToLoad + ")",
                    host: BASE_URL,
                });
            }
        });
        
        allBooks = allBooks.concat(pageBooks);
        
        // Nếu là trang cuối hoặc không có books, dừng load
        if (isLastPage || pageBooks.length === 0) {
            break;
        }
    }
    
    // Calculate next page
    let nextPage = "";
    if (allBooks.length > 0) {
        nextPage = (currentPage + maxPagesToLoad).toString();
    }
    
    return Response.success(allBooks, nextPage);
}