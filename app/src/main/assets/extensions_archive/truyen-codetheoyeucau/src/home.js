function execute() {
    return Response.success([
        {title: "Mới Cập Nhật", input: "https://truyen.codetheoyeucau.online/api/search/stories?sort=newest&limit=20&page=", script: "gen.js"},
        {title: "Thịnh Hành", input: "https://truyen.codetheoyeucau.online/api/search/stories?sort=trending&limit=20&page=", script: "gen.js"},
        {title: "Hoàn Thành", input: "https://truyen.codetheoyeucau.online/api/search/stories?status=COMPLETED&limit=20&page=", script: "gen.js"}
    ]);
}
