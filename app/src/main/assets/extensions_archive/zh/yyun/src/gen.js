function execute(url, page) {
	url = url.replace('m.yanyunxs.com', 'www.yanyunxs.com');
    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
		doc.select("#newscontent .l li").forEach(e => {
            data.push({
                name: e.select(".s2 a").first().text(),
                link: e.select(".s2 a").first().attr("href"),
                description: e.select(".s3 a").first().text(),
                host: "http://www.yanyunxs.com"
            })
        });

		if (data.length === 0) {
			doc.select(".novelslist2 li").forEach(e => {
                if(e.select("a").first().text() !== null && e.select("a").first().text() !== '') {
                    data.push({
                        name: e.select("a").first().text(),
                        link: e.select("a").first().attr("href"),
                        description: e.select(".s3 a").first().text(),
                        host: "http://www.yanyunxs.com"
                    })
			    }
            }
            ); 
		}


        return Response.success(data)
    }
    return null;
}