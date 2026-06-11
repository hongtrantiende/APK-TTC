load("config.js");

// Fetch list data from site's JSON API instead of scraping HTML
function execute(url, page) {
    if (!page) page = 1;

    // Build API URL
    let api = BASE_URL.replace(/\/$/, '') + '/api/v2/search?p=' + page + '&searchValue=&orders%5B%5D=recentDate';
    let response = fetch(api);
    if (!response.ok) return null;

    try {
        let data = response.json();
        if (!data || !data.result || !data.result.data) return null;

        let books = [];
        data.result.data.forEach(item => {
            // cover/photo may be relative
            let photo = item.photo || '';
            let cover = photo.startsWith('http') ? photo : BASE_URL.replace(/\/$/, '') + (photo.startsWith('/') ? photo : '/' + photo);

            // detail link uses nameEn slug when available
            let slug = item.nameEn || '';
            let link = BASE_URL.replace(/\/$/, '') + '/truyen/' + slug;

            let description = item.description || (item.category ? item.category.join(', ') : '');

            books.push({
                name: item.name || '',
                link: link,
                cover: cover,
                description: description,
                host: BASE_URL,
                id: item.id || ''
            });
        });

        // handle pagination: API returns result.next boolean and result.p
        let next = null;
        if (data.result.next) {
            next = (parseInt(data.result.p, 10) + 1).toString();
        }

        if (next) return Response.success(books, next);
        return Response.success(books);
    } catch (e) {
        return null;
    }
}