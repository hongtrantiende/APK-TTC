load('config.js');

function execute() {
    return Response.success([
        { title: "Tiên Hiệp", input: "/the-loai/tien-hiep", script: "gen.js" },
        { title: "Ngôn Tình", input: "/the-loai/ngon-tinh", script: "gen.js" },
        { title: "Hiện Đại", input: "/the-loai/hien-dai", script: "gen.js" },
        { title: "Cổ Đại", input: "/the-loai/co-dai", script: "gen.js" },
        { title: "Huyền Huyễn", input: "/the-loai/huyen-huyen", script: "gen.js" },
        { title: "Đô Thị", input: "/the-loai/do-thi", script: "gen.js" },
        { title: "Khoa Huyễn", input: "/the-loai/khoa-huyen", script: "gen.js" },
        { title: "Linh Dị", input: "/the-loai/linh-di", script: "gen.js" },
        { title: "Trinh Thám", input: "/the-loai/trinh-tham", script: "gen.js" },
        { title: "Võ Hiệp", input: "/the-loai/vo-hiep", script: "gen.js" },
        { title: "Đồng Nhân", input: "/the-loai/dong-nhan", script: "gen.js" },
        { title: "Xuyên Không", input: "/the-loai/xuyen-khong", script: "gen.js" },
        { title: "Trọng Sinh", input: "/the-loai/trong-sinh", script: "gen.js" },
        { title: "Hoàn Thành", input: "/the-loai/hoan-thanh", script: "gen.js" },
        { title: "Miễn Phí", input: "/the-loai/mien-phi", script: "gen.js" }
    ]);
}
