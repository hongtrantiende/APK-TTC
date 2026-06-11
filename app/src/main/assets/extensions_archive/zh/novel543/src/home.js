load("config.js");

function execute() {
    // Warmup CF on first load — solves challenge once, caches cookies
    warmupCF();

    return Response.success([
        { title: "\u7cbe\u54c1\u63a8\u85a6", input: "home", script: "homecontent.js" },
        { title: "\u6700\u65b0\u66f4\u65b0", input: "bookstack", script: "homecontent.js" },
        { title: "\u5df2\u5b8c\u7d50", input: "bookstack_end", script: "homecontent.js" },
        { title: "\u7537\u751f", input: "bookstack_boy", script: "homecontent.js" },
        { title: "\u5973\u751f", input: "bookstack_girl", script: "homecontent.js" }
    ]);
}