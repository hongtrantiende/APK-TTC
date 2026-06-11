load('config.js');

function execute() {
    return Response.success([
        {title: "Tien Hiep", input: BASE_URL + "/the-loai/tien-hiep", script: "gen.js"},
        {title: "Kiem Hiep", input: BASE_URL + "/the-loai/kiem-hiep", script: "gen.js"},
        {title: "Ngon Tinh", input: BASE_URL + "/the-loai/ngon-tinh", script: "gen.js"},
        {title: "Dam My", input: BASE_URL + "/the-loai/dam-my", script: "gen.js"},
        {title: "Bach Hop", input: BASE_URL + "/the-loai/bach-hop", script: "gen.js"},
        {title: "Do Thi", input: BASE_URL + "/the-loai/do-thi", script: "gen.js"},
        {title: "Quan Truong", input: BASE_URL + "/the-loai/quan-truong", script: "gen.js"},
        {title: "Vong Du", input: BASE_URL + "/the-loai/vong-du", script: "gen.js"},
        {title: "Khoa Huyen", input: BASE_URL + "/the-loai/khoa-huyen", script: "gen.js"},
        {title: "He Thong", input: BASE_URL + "/the-loai/he-thong", script: "gen.js"},
        {title: "Huyen Huyen", input: BASE_URL + "/the-loai/huyen-huyen", script: "gen.js"},
        {title: "Di Gioi", input: BASE_URL + "/the-loai/di-gioi", script: "gen.js"},
        {title: "Di Nang", input: BASE_URL + "/the-loai/di-nang", script: "gen.js"},
        {title: "Quan Su", input: BASE_URL + "/the-loai/quan-su", script: "gen.js"},
        {title: "Lich Su", input: BASE_URL + "/the-loai/lich-su", script: "gen.js"},
        {title: "Xuyen Khong", input: BASE_URL + "/the-loai/xuyen-khong", script: "gen.js"},
        {title: "Xuyen Nhanh", input: BASE_URL + "/the-loai/xuyen-nhanh", script: "gen.js"},
        {title: "Trong Sinh", input: BASE_URL + "/the-loai/trong-sinh", script: "gen.js"},
        {title: "Trinh Tham", input: BASE_URL + "/the-loai/trinh-tham", script: "gen.js"},
        {title: "Linh Di", input: BASE_URL + "/the-loai/linh-di", script: "gen.js"},
        {title: "Nguoc", input: BASE_URL + "/the-loai/nguoc", script: "gen.js"},
        {title: "Sac", input: BASE_URL + "/the-loai/sac", script: "gen.js"},
        {title: "Sung", input: BASE_URL + "/the-loai/sung", script: "gen.js"}
    ]);
}
