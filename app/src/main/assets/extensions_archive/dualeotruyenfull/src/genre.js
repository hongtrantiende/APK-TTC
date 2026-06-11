load("config.js");

function execute() {
    return Response.success([
        { title: "Đam Mỹ",     input: "dammy",     script: "genrecontent.js" },
        { title: "Cao H",      input: "caoh",      script: "genrecontent.js" },
        { title: "Hiện đại",   input: "hiendai",   script: "genrecontent.js" },
        { title: "Song tính",  input: "songtinh",  script: "genrecontent.js" },
        { title: "Danmei",     input: "danmei",    script: "genrecontent.js" },
        { title: "H văn",      input: "hvan",      script: "genrecontent.js" },
        { title: "Sủng",       input: "sung",      script: "genrecontent.js" },
        { title: "1v1",        input: "1v1",       script: "genrecontent.js" },
        { title: "Ngôn tình",  input: "ngontinh",  script: "genrecontent.js" },
        { title: "LGBT",       input: "lgbt",      script: "genrecontent.js" },
    ]);
}
