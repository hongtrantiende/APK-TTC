load('config.js');
function execute(link) {
    var response = fetchPage(link);
    if (response.ok) {
        var doc = response.html();
        
        var name = '';
        var cover = '';
        var author = '';
        var authorUrl = '';
        var status = 'Đang ra';
        var description = '';
        var genres = [];
        var genreTexts = [];
        var suggests = [];
        
        try {
            var titleEl = doc.select('article h1, h1 .title-truyen, h1').first();
            if (titleEl) {
                name = titleEl.text().replace(/\s+/g, ' ').trim();
            }

            var coverEl = doc.select('.image-truyen img, article img[alt], article img').first();
            if (coverEl) {
                cover = coverEl.attr('data-src') || coverEl.attr('src') || '';
                if (cover && cover.indexOf('http') !== 0) {
                    cover = cover.charAt(0) === '/' ? BASE_URL + cover : BASE_URL + '/' + cover;
                }
            }

            var infoItems = doc.select('ul.info-truyen li');
            for (var i = 0; i < infoItems.size(); i++) {
                var item = infoItems.get(i);
                var text = item.text() || '';
                var lowerText = text.toLowerCase();
                
                if (lowerText.indexOf('tác giả:') > -1) {
                    var authorA = item.select('a').first();
                    if (authorA) {
                        author = authorA.text().replace(/\s+/g, ' ').trim();
                        authorUrl = authorA.attr('href') || '';
                    }
                    if (!author) {
                        author = text.replace(/tác giả:/i, '').replace(/\s+/g, ' ').trim();
                    }
                } else if (lowerText.indexOf('thể loại:') > -1) {
                    var cats = item.select('a');
                    for (var j = 0; j < cats.size(); j++) {
                        var catA = cats.get(j);
                        var catName = catA.text().replace(/\s+/g, ' ').trim();
                        var catHref = catA.attr('href') || '';
                        if (catHref && catHref.indexOf('http') !== 0) {
                            catHref = catHref.charAt(0) === '/' ? BASE_URL + catHref : BASE_URL + '/' + catHref;
                        }
                        if (catName && catHref) {
                            genres.push({
                                title: catName,
                                input: catHref,
                                script: 'gen.js'
                            });
                            genreTexts.push(catName);
                        }
                    }
                } else if (lowerText.indexOf('trạng thái:') > -1) {
                    status = text.replace(/trạng thái:/i, '').replace(/\s+/g, ' ').trim();
                }
            }

            if (!author) {
                var metaAuthor = doc.select('meta[name="author"], meta[property="author"]').first();
                if (metaAuthor) {
                    author = metaAuthor.attr('content').replace(/\s+/g, ' ').trim();
                }
            }

            if (!author) {
                author = 'Đang cập nhật';
            }

            if (!status) {
                status = 'Đang ra';
            }

            var descEl = doc.select('.noi-dung');
            if (descEl.size() > 0) {
                description = descEl.text().replace(/\s+/g, ' ').trim().substring(0, 500);
            }
            
            if (authorUrl) {
                if (authorUrl.indexOf('http') !== 0) {
                    authorUrl = authorUrl.charAt(0) === '/' ? BASE_URL + authorUrl : BASE_URL + '/' + authorUrl;
                }
                suggests.push({
                    title: 'Truyện cùng tác giả',
                    input: authorUrl,
                    script: 'gen.js'
                });
            }
            if (genres.length > 0) {
                suggests.push({
                    title: 'Truyện cùng thể loại',
                    input: genres[0].input,
                    script: 'gen.js'
                });
            }
            
        } catch (err) {
        }
        
        var lowerStatus = status.toLowerCase();
        var ongoing = lowerStatus.indexOf('hoàn thành') === -1 &&
                      lowerStatus.indexOf('đã hoàn') === -1 &&
                      lowerStatus.indexOf('full') === -1 &&
                      lowerStatus.indexOf('complete') === -1;
                      
        var detailParts = [];
        detailParts.push('<p><strong>Tác giả:</strong> ' + author + '</p>');
        if (genreTexts.length > 0) {
            detailParts.push('<p><strong>Thể loại:</strong> ' + genreTexts.join(', ') + '</p>');
        }
        detailParts.push('<p><strong>Trạng thái:</strong> ' + status + '</p>');
        
        var detailInfo = detailParts.join('');

        return Response.success({
            name: name,
            cover: cover,
            host: BASE_URL,
            author: author,
            status: status,
            description: description,
            detail: detailInfo,
            ongoing: ongoing,
            genres: genres,
            suggests: suggests,
            category: genreTexts.length > 0 ? genreTexts[0] : '',
            categories: genreTexts,
            link: link
        });
    }
    return Response.error('HTTP Error: ' + response.status);
}
