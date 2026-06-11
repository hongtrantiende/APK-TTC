function execute(url) {
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html('utf-8');
        let el = doc.select("div#volumes div div a")
        const data = [];
        for (let i = 0;i < el.size(); i++) {
            var e = el.get(i);
            let chapter_id = e.attr("href");
            data.push({
                name: e.select("a").text(),
                url: "https://lnovel.org/" + chapter_id,
                host: "https://lnovel.org/"
            })
        }
        return Response.success(data);
    }
    return null;
}