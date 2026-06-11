load('config.js');
function execute() {
    return Response.success([
        { title: "Huyền Huyễn",input: BASE_URL + "/the-loai/huyen-huyen",script: "gen.js" },
        { title: "Đô Thị",input: BASE_URL + "/the-loai/do-thi",script: "gen.js" },
        { title: "Kỳ Huyễn",input: BASE_URL + "/the-loai/ky-huyen",script: "gen.js" },
        { title: "Võng Du",input: BASE_URL + "/the-loai/vong-du",script: "gen.js" },
        { title: "Linh Dị",input: BASE_URL + "/the-loai/linh-di",script: "gen.js" },
        { title: "Light Novel",input: BASE_URL + "/the-loai/light-novel",script: "gen.js" },
        { title: "Tiên Hiệp",input: BASE_URL + "/the-loai/tien-hiep",script: "gen.js" },
        { title: "Võ Hiệp",input: BASE_URL + "/the-loai/vo-hiep",script: "gen.js" },
        { title: "Ngôn Tình",input: BASE_URL + "/the-loai/ngon-tinh",script: "gen.js" },
        { title: "Khoa Huyễn",input: BASE_URL + "/the-loai/khoa-huyen",script: "gen.js" },
        { title: "Lịch Sử Quân Sự",input: BASE_URL + "/the-loai/lich-su-quan-su",script: "gen.js" },
        { title: "Đồng Nhân",input: BASE_URL + "/the-loai/dong-nhan",script: "gen.js" },
        { title: "Cạnh Kỹ",input: BASE_URL + "/the-loai/canh-ky",script: "gen.js" }
    ]);
}