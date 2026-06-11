function execute(url, page) {
    if (page && page > 1) return Response.success([]);

    return Response.success([
        {
            title: "Hướng dẫn sử dụng VBook",
            input: "https://vbookapp.gitbook.io/huong-dan-su-dung",
            script: "gen.js",
            author: "ngatngay, duongden",
            cover: "https://i.ibb.co/B59c82qz/vbooks.png"
        }
    ]);
}
