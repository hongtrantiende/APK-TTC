load("config.js");

function execute() {
    return Response.success([
        { title: "Ngôn tình đô thị",       input: "dsyq",      script: "genrecontent.js" },
        { title: "Võ hiệp tu tiên",         input: "wxxz",      script: "genrecontent.js" },
        { title: "Huyền hư kỳ ảo",         input: "xhqh",      script: "genrecontent.js" },
        { title: "Xuyên không dị đại",      input: "cyjk",      script: "genrecontent.js" },
        { title: "Khoa viễn cạnh tranh",    input: "khjj",      script: "genrecontent.js" },
        { title: "Ma quỷ kinh dị",          input: "ghxy",      script: "genrecontent.js" },
        { title: "Quân sự lịch sử",         input: "jsls",      script: "genrecontent.js" },
        { title: "Chính trường thương chiến", input: "guanchang", script: "genrecontent.js" },
        { title: "Thôn quê phong tục",      input: "xtfq",      script: "genrecontent.js" },
        { title: "Truyện BL",               input: "dmtr",      script: "genrecontent.js" },
        { title: "Fan fiction",             input: "trxs",      script: "genrecontent.js" },
        { title: "Tinh phẩm",               input: "jqxs",      script: "genrecontent.js" },
    ]);
}