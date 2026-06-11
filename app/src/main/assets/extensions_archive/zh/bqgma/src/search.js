load('config.js');
function execute(key) {
    let response = fetch("https://m.bqgma.com/user/search.html?q=" + key + "&so=undefined");
    if (response.ok) {
        let json = response.json();
        let books = [];
        json.forEach(e => {
            books.push({
                name: e.articlename,
                link: e.url_list,
                cover: e.url_img,
                description: e.author,
                host: BASE_URL
            })
        });
        return Response.success(books);
    }
    return null;
}