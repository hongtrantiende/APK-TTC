load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);
    let response = fetch(BASE_URL + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];


        let novels = doc.select("#popular li");

        novels.forEach(e => {

            let novelUrl = BASE_URL + e.select("a").first().attr("href");

            let novelResponse = fetch(novelUrl);
            if (novelResponse.ok) {
                let novelDoc = novelResponse.html();


                let coverUrl = novelDoc.select("dl .binfo img").first().attr("src");


                data.push({
                    name: e.select("a").first().text(),
                    link: novelUrl,
                    description: e.select("span").first().text(),
                    cover: coverUrl,
                    host: BASE_URL
                });
            }
        });

        return Response.success(data);
    }
    return null;
}