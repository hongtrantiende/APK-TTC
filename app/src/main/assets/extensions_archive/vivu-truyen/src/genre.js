load('config.js');
function execute() {
    var response = fetchPage(BASE_URL);
    if (!response.ok) return Response.error('HTTP Error: ' + response.status);

    var doc = response.html();
    var data = [];
    var seen = {};

    function addCategory(title, input) {
        if (!title || !input || seen[input]) return;
        seen[input] = true;
        data.push({ title: title, input: input, script: 'gen.js' });
    }

    var categories = doc.select('ul.danh-muc a.menu-item');
    for (var i = 0; i < categories.size(); i++) {
        var el = categories.get(i);
        var title = el.text().replace(/\s+/g, ' ').trim();
        var href = el.attr('href') || '';
        if (!title || !href) continue;

        if (href.indexOf('http') !== 0) {
            href = href.charAt(0) === '/' ? BASE_URL + href : BASE_URL + '/' + href;
        }
        addCategory(title, href);
    }

    if (data.length === 0) {
        addCategory('Hiện Đại', BASE_URL + '/hien-dai/');
    }

    return Response.success(data);
}
