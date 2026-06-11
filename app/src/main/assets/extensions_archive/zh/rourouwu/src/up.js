function execute(url) {
    url = url.replace("www.rourouwu.com", "wap.rourouwu.com");
    let response = fetch("https://wap.rourouwu.com" + url);
    if (response.ok) {
        let doc = response.html();
        const data = [];
        doc.select(".s_m ul li").forEach(e => {
            data.push({
                name: e.select("a").first().text(),
                link: "https://wap.rourouwu.com" + e.select("a").first().attr("href"),
                description: e.select(".info a").first().text()||e.select(".info span").text(),
                host: "https://wap.rourouwu.com"
            })
        });
        return Response.success(data)
    }
    return null;
}