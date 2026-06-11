load("config.js");

function execute() {
    return Response.success([
        { title: "Moi Cap Nhat", input: "latest", script: "homecontent.js" },
        { title: "Hoan Thanh", input: "hoan-thanh", script: "genrecontent.js" },
        { title: "Dang Tien Hanh", input: "dang-tien-hanh", script: "genrecontent.js" }
    ]);
}