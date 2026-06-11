function execute() {
    return Response.success([
        { title: "Truyện Mới Update", input: "https://webtruyendich.com/tim-truyen?sort=update", script: "source.js" },
        { title: "Bảng xếp hạng đọc nhiều", input: "https://webtruyendich.com/tim-truyen?sort=view-all", script: "source.js" },
    ]);
}