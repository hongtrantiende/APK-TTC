function execute() {
    return Response.success([
        {title: "Truyện Membership", input: "https://novest.me/tim-kiem?membership=true", script: "gen.js"},
        {title: "Truyện Miễn Phí", input: "https://novest.me/tim-kiem?vipStatus=NONE", script: "gen.js"},
        {title: "Truyện Mới Cập Nhật", input: "https://novest.me/tim-kiem?sort=updated", script: "gen.js"}
    ]);
}