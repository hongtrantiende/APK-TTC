function execute(url, page) {
    if (!page) page = '1';

    let fetchUrl = url;
    if (url.startsWith("/")) {
        fetchUrl = "https://truyenchuhay.org" + url;
    }

    if (page !== '1') {
        fetchUrl += "?page=" + page;
    }

    let response = fetch(fetchUrl);

    if (response.ok) {
        let doc = response.html();
        let novels = [];

        // Use the grid that wraps novel items
        let elements = doc.select(".grid.grid-cols-2.gap-3 > div, .grid.grid-cols-12 > div.mt-3, .grid.grid-cols-12 > div.col-span-12.lg\\:col-span-6"); // Various selectors due to responsive design on lists

        // Actually, let's look at truyen-moi page structure we saved:
        // It has a list of items: <div class="grid grid-cols-12 h-10 border-b border-dashed ...">
        // Wait, the lists in /danh-sach/* are different from the /danh-sach/*?page=X layout? 
        // Let's use a robust selector that covers both or write specific logic.

        // The previously downloaded truyenchuhay_gen.html uses:
        // <div itemScope itemType="https://schema.org/Book">
        // Inside it a href, inside it img, h3(name).

        elements = doc.select(".grid.grid-cols-12.border-b.border-dashed");

        elements.forEach(e => {
            let h3a = e.select("h3 a").first();
            let link = h3a.attr("href");
            let name = h3a.text();

            if (!name) {
                name = e.select("h3").text();
                link = e.select("a").first().attr("href"); // Fallback
            }

            let img = e.select("img").attr("src") || e.select("img").attr("data-src");

            // Look for the chapter link for description
            let description = e.select("a.block.mt-auto").text();
            if (!description) {
                description = e.select(".lg\\:col-span-3 a").text() || e.select("small").text();
            }

            if (name && link) {
                novels.push({
                    name: name,
                    link: link,
                    cover: img || "https://truyenchuhay.org/images/logo.png",
                    description: description,
                    host: "https://truyenchuhay.org"
                });
            }
        });

        // Pagination
        // Look for element with "Trang tiếp"
        let next = "";
        let pagination = doc.select("ul.pagination li a");
        pagination.forEach(a => {
            if (a.text().indexOf("Sau") !== -1 || a.text().indexOf(">") !== -1 || a.attr("aria-label") === "Next") {
                let href = a.attr("href");
                let match = href.match(/page=(\d+)/);
                if (match) {
                    next = match[1];
                }
            }
        });

        if (!next) {
            // Alternative pagination check
            let nextPageBtn = doc.select("a.inline-flex.items-center.justify-center.w-10.h-10").last();
            if (nextPageBtn && nextPageBtn.html().includes("M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3") || nextPageBtn.attr("href")) {
                let href = nextPageBtn.attr("href");
                let match = href.match(/page=(\d+)/);
                if (match) {
                    next = match[1];
                }
            }
        }

        return Response.success(novels, next);
    }

    return null;
}
