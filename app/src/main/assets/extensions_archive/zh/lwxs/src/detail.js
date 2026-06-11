load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();

        let name = doc.select("h1.f21h").text();
        let author = doc.select("h1.f21h em a").text();
        let cover = doc.select(".pic em img").attr("src");
        let description = doc.select(".intro").text();
        
        // Extract details from div > a
        let detail = "";
        doc.select("div > a").forEach(e => {
            detail += e.text() + "<br>";
        });

        return Response.success({
            name: name,
            author: author,
            cover: cover,
            description: description,
            detail: detail,
            host: BASE_URL
        });
    }
    return null;
}