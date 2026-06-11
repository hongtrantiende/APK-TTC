load("config.js");

function execute() {
    return Response.success([
        { title: "Chinese Novels", input: "/genre/chinese-novel/", script: "genrecontent.js" },
        { title: "Japanese Novels", input: "/genre/japanese-novel/", script: "genrecontent.js" },
        { title: "Korean Novels", input: "/genre/korean-novel/", script: "genrecontent.js" },
        { title: "BG", input: "/genre/bg/", script: "genrecontent.js" },
        { title: "Romance", input: "/genre/romance/", script: "genrecontent.js" },
        { title: "Smut", input: "/genre/smut/", script: "genrecontent.js" },
        { title: "Historical", input: "/genre/historical/", script: "genrecontent.js" },
        { title: "Incest", input: "/genre/incest/", script: "genrecontent.js" }
    ]);
}