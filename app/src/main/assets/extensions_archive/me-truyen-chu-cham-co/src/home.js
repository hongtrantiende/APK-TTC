function execute() {
    return Response.success([
        {title: "Mới hoàn thành", input: "https://metruyenchu.co/danh-sach?sort=createdAt&status=COMPLETED", script: "gen.js"},
        {title: "Mới cập nhật", input: "https://metruyenchu.co/danh-sach?sort=createdAt", script: "gen.js"},
    ]);
}