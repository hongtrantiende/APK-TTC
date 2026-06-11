load('config.js');

function execute() {
    return Response.success([
        {title: "Moi cap nhat", input: BASE_URL + "/novel?sort=updated", script: "gen.js"},
        {title: "Moi dang", input: BASE_URL + "/novel?sort=created", script: "gen.js"},
        {title: "Hot nhat", input: BASE_URL + "/hot", script: "gen.js"},
        {title: "Danh sach", input: BASE_URL + "/novel", script: "gen.js"},
        {title: "Hoan thanh", input: BASE_URL + "/novel?status=completed&sort=updated", script: "gen.js"}
    ]);
}
