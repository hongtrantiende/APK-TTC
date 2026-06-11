load('config.js');

function execute() {
    return Response.success([
        {title: "Truyen hot", input: "/danh-sach/truyen-hot", script: "gen.js"},
        {title: "Truyen full", input: "/danh-sach/truyen-full", script: "gen.js"},
        {title: "Truyen ngon tinh", input: "/the-loai/ngon-tinh", script: "gen.js"},
        {title: "Tien hiep", input: "/the-loai/tien-hiep", script: "gen.js"},
        {title: "Kiem hiep", input: "/the-loai/kiem-hiep", script: "gen.js"},
        {title: "Huyen huyen", input: "/the-loai/huyen-huyen", script: "gen.js"},
        {title: "Do thi", input: "/the-loai/do-thi", script: "gen.js"},
        {title: "Xuyen khong", input: "/the-loai/xuyen-khong", script: "gen.js"}
    ]);
}
