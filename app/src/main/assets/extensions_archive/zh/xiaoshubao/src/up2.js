load('config.js');
function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        let novels = doc.select("#newscontent .r ul li");
        novels.forEach(e => {
            let novelUrl = BASE_URL + e.select(".s2 a").first().attr("href");
            let novelResponse = fetch(novelUrl);
            if (novelResponse.ok) {
                let novelDoc = novelResponse.html();
                let coverUrl = novelDoc.select("#fmimg img").first().attr("src");
                data.push({
                    name: e.select(".s2 a").first().text(),
                    link: novelUrl,
                    description: e.select(".s5").first().text(),
                    cover: BASE_URL + coverUrl,
                    host: BASE_URL
                });
            }
        });
        return Response.success(data);
    }
    return null;
}