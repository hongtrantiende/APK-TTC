load("config.js");

function execute(url) {
    let chapters = [];
    let currentUrl = url;
    let pageNum = 1;
    let maxPages = 50; // Safety limit to prevent infinite loops
    let previousChapterCount = 0;

    while (pageNum <= maxPages) {
        let response = fetch(currentUrl);
        if (!response.ok) {
            break;
        }

        let doc = response.html();
        let currentPageChapters = 0;

        // Get chapter lists from all sections
        let chapterSections = doc.select("#newlist");
        
        chapterSections.forEach(section => {
            let chapterLinks = section.select("ul li a");
            
            chapterLinks.forEach(link => {
                let chapterTitle = link.text();
                let chapterUrl = link.attr("href");
                
                // Make sure URL is absolute
                if (chapterUrl && !chapterUrl.startsWith("http")) {
                    chapterUrl = BASE_URL + chapterUrl;
                }
                
                // Avoid duplicates by checking if chapter already exists
                let isDuplicate = chapters.some(chapter => 
                    chapter.url === chapterUrl || chapter.name === chapterTitle
                );
                
                if (!isDuplicate && chapterTitle && chapterUrl) {
                    chapters.push({
                        name: chapterTitle,
                        url: chapterUrl,
                        host: BASE_URL,
                    });
                    currentPageChapters++;
                }
            });
        });

        // If no new chapters were added, we've reached the end
        if (currentPageChapters === 0 && pageNum > 1) {
            break;
        }

        // Check for next page link
        let nextPageLink = doc.select("a.next").attr("href") ||
                          doc.select("a[rel='next']").attr("href") ||
                          doc.select(".pagenavi a:contains('下一頁')").attr("href") ||
                          doc.select(".pagenavi a:contains('下一页')").attr("href");

        if (!nextPageLink) {
            // Try to increment page parameter if URL has page parameter
            let pageMatch = currentUrl.match(/[?&]page=(\d+)/);
            if (pageMatch) {
                let nextPageNum = parseInt(pageMatch[1]) + 1;
                currentUrl = currentUrl.replace(/[?&]page=\d+/, "&page=" + nextPageNum);
                nextPageLink = currentUrl;
            } else if (currentUrl.includes("?")) {
                currentUrl = currentUrl + "&page=2";
                nextPageLink = currentUrl;
            } else {
                break;
            }
        }

        if (!nextPageLink) {
            break;
        }

        // Make next page URL absolute
        if (nextPageLink && !nextPageLink.startsWith("http")) {
            nextPageLink = BASE_URL + nextPageLink;
        }

        currentUrl = nextPageLink;
        pageNum++;
    }

    // Sort chapters by chapter number if possible
    chapters.sort((a, b) => {
        // Extract chapter numbers from titles (handles 第N章, Chapter N, etc.)
        let aMatch = a.name.match(/[第#](\d+)[章]?/);
        let bMatch = b.name.match(/[第#](\d+)[章]?/);
        
        if (aMatch && bMatch) {
            return parseInt(aMatch[1]) - parseInt(bMatch[1]);
        }
        
        // If can't extract numbers, keep original order
        return 0;
    });

    if (chapters.length === 0) {
        return Response.error("Không thể tải trang chapter list");
    }

    return Response.success(chapters);
}