function execute(url) {
    if (url.startsWith("/")) {
        url = "https://truyenchuhay.org" + url;
    }
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let content = doc.select("#content-chapter");

        // Remove empty or ad paragraphs
        content.select("p.overflow-x-hidden").forEach(p => {
            if (!p.text().trim() || p.text().includes("PS:")) {
                p.remove();
            }
        });

        // Remove known ads or garbage
        content.select("#custom-ad-slot").remove();

        // Sometimes text is directly in paragraphs
        let html = content.html();

        // Basic cleanup
        html = html.replace(/^<div[^>]*>/, '').replace(/<\/div>$/, '');

        return Response.success(html);
    }
    return null;
}
