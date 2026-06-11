load("config.js");

function execute() {
    return Response.success([
        { title: "Hoan Thanh", input: "hoan-thanh", script: "genrecontent.js" },
        { title: "Dang Tien Hanh", input: "dang-tien-hanh", script: "genrecontent.js" }
    ]);
}