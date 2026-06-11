load('config.js');
function execute() {
    return Response.success([
           { title: 'Truyện Đề Cử', input: BASE_URL + '/de-cu/', script: 'gen.js' },
           { title: 'Xem Nhiều', input: BASE_URL + '/xem-nhieu/', script: 'gen.js' },
           { title: 'Mới Cập Nhật', input: BASE_URL + '/moi-cap-nhat/', script: 'gen.js' },
           { title: 'Mới Nhất', input: BASE_URL + '/moi-nhat/', script: 'gen.js' },
           { title: 'Truyện Full', input: BASE_URL + '/full/', script: 'gen.js' }
    ]);
}
