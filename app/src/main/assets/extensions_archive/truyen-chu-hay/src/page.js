function execute(url) {
    let bookId = url.split('/').pop().split('?')[0]; // Extract base slug
    let fetchUrl = "https://truyenchuhay.org/" + bookId;

    let response = fetch(fetchUrl);
    if (!response.ok) return null;
    let doc = response.html();

    let totalPages = 1;
    let lastPageLink = doc.select("ul.list-page li a:contains(>>)");
    if (lastPageLink.length > 0) {
        let href = lastPageLink.attr("href");
        let match = href.match(/page=(\d+)/);
        if (match) {
            totalPages = parseInt(match[1]);
        }
    } else {
        // Find highest number in pagination
        doc.select("ul.list-page li a").forEach(e => {
            let num = parseInt(e.text());
            if (!isNaN(num) && num > totalPages) {
                totalPages = num;
            }
        });
    }

    let countNum = 0;
    doc.select("span.col-span-1").forEach(e => {
        let text = e.text().toLowerCase();
        if (text.includes("chương")) {
            let countStr = e.select("strong").text().replace(/,/g, '').trim();
            countNum = parseInt(countStr);
        }
    });

    if (countNum > 0) {
        let calcPages = Math.ceil(countNum / 50);
        if (calcPages > totalPages) {
            totalPages = calcPages;
        }
    }

    let pages = [];
    for (let i = 1; i <= totalPages; i++) {
        pages.push(fetchUrl + "?page=" + i);
    }

    return Response.success(pages);
}
