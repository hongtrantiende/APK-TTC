load("config.js");
function execute(input) {
    if (!input) return null;

    let data = [];
    try {
        data = JSON.parse(input);
    } catch (e) {
        return null;
    }

    let books = [];
    data.forEach(item => {
        let cover = item.image ? (IMAGE_BASE + item.image + ".jpg") : "";
        books.push({
            name: item.name,
            link: item.id + "",
            cover: cover,
            description: item.author || "",
            host: BASE_URL
        });
    });

    return Response.success(books);
}
