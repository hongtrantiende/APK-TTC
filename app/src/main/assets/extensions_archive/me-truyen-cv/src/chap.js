load("config.js");

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL)
    let response = fetch(url);

    if (response.ok) {
        let doc = response.html();
        let content = doc.select(".text-left").html();
        return Response.success(content);
    }
    return null;
}