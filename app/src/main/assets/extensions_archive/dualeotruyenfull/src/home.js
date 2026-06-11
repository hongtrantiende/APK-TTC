load("config.js");

function execute() {
    return Response.success([
        { title: "Mới cập nhật", input: "moi-cap-nhat", script: "homecontent.js" },
        { title: "Thịnh hành",   input: "dang-thinh-hanh", script: "homecontent.js" },
        { title: "Hoàn thành",   input: "truyen-da-hoan-thanh", script: "homecontent.js" },
    ]);
}
