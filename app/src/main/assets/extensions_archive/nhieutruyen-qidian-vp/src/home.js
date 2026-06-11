function execute() {
    return Response.success([
        { title: "Truyện mới cập nhật", input: "https://nhieutruyen.com/danh-sach/truyen-moi", script: "gen.js" },
        { title: "Truyện hoàn thành", input: "https://nhieutruyen.com/danh-sach/truyen-hoan-thanh", script: "gen.js" },
        { title: "Truyện tạm ngưng", input: "https://nhieutruyen.com/danh-sach/truyen-tam-ngung", script: "gen.js" },
        { title: "Lượt đọc", input: "https://nhieutruyen.com/xep-hang/luot-doc", script: "gen.js" },
        { title: "Đề cử", input: "https://nhieutruyen.com/xep-hang/de-cu", script: "gen.js" },
    ]);
}
