load("config.js");

function execute(key, page) {
    if (!page) page = '1';
    
    // Build search URL for wfxs.tw
    let searchUrl = BASE_URL + "/s.html?type=articlename&s=" + encodeURIComponent(key);
    if (page !== '1') {
        searchUrl += "&page=" + page;
    }
    
    let response = fetch(searchUrl);
    if (response.ok) {
        let doc = response.html();

        // Extract next page from pagination
        let nextPage = "";
        let paginationLinks = doc.select(".pagination a");
        
        for (let i = 0; i < paginationLinks.size(); i++) {
            let link = paginationLinks.get(i);
            let text = link.text();
            
            if (text.includes("下一頁") || text.includes("下页")) {
                let href = link.attr("href");
                if (href) {
                    let pageMatch = href.match(/page=(\d+)/);
                    if (pageMatch) {
                        nextPage = pageMatch[1];
                    }
                }
                break;
            }
        }

        let books = [];
        doc.select(".result-item").forEach(e => {
            let titleElement = e.select("h3 a").first();
            let authorElement = e.select("p").get(0); // First p tag contains author info
            let descElement = e.select("p").get(1);   // Second p tag contains description
            let coverElement = e.select("img").first();
            
            if (titleElement) {
                let title = titleElement.text();
                let link = titleElement.attr("href");
                let author = authorElement ? authorElement.text() : "";
                let description = descElement ? descElement.text() : "";
                let cover = coverElement ? coverElement.attr("src") : "";
                
                // Make sure link is absolute
                if (link && !link.startsWith("http")) {
                    link = BASE_URL + link;
                }
                
                // Make sure cover is absolute
                if (cover && !cover.startsWith("http")) {
                    cover = BASE_URL + cover;
                }
                
                books.push({
                    name: title,
                    link: link,
                    cover: cover,
                    description: author + (description ? " | " + description : ""),
                    host: BASE_URL,
                });
            }
        });

        return Response.success(books, nextPage);
    }
    return null;
}