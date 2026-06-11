load('config.js');
function execute() {
    let response = fetch(BASE_URL);
    if (response.ok) {
        let doc = response.html();
        let genre = [];
        doc.select('ul.list-unstyled li a').forEach(e => {
            genre.push({
                title: e.text().trim(),
                input: e.attr('href'),
                script: 'book.js'
            });
        });
        return Response.success(genre);
    }
    return null;
}