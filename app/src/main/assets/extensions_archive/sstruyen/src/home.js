load('config.js');

function execute() {
    return Response.success([
        {title: "Truyen Hot", input: BASE_URL + "/danh-sach/truyen-hot", script: "gen.js"},
        {title: "Truyen Full", input: BASE_URL + "/danh-sach/truyen-full", script: "gen.js"},
        {title: "Ngon Tinh Hay", input: BASE_URL + "/danh-sach/truyen-ngon-tinh-hay", script: "gen.js"},
        {title: "Ngon Tinh Nguoc", input: BASE_URL + "/danh-sach/truyen-ngon-tinh-nguoc", script: "gen.js"},
        {title: "Ngon Tinh Sung", input: BASE_URL + "/danh-sach/truyen-ngon-tinh-sung", script: "gen.js"},
        {title: "Dam My Hay", input: BASE_URL + "/danh-sach/truyen-dam-my-hay", script: "gen.js"},
        {title: "Truyen Teen Hay", input: BASE_URL + "/danh-sach/truyen-teen-hay", script: "gen.js"},
        {title: "Kiem Hiep Hay", input: BASE_URL + "/danh-sach/truyen-kiem-hiep-hay", script: "gen.js"},
        {title: "Tien Hiep Hay", input: BASE_URL + "/danh-sach/truyen-tien-hiep-hay", script: "gen.js"}
    ]);
}
