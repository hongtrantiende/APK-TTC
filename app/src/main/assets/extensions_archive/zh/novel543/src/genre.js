load("config.js");

function execute() {
    return Response.success([
        { title: "\u7384\u5e7b (Huyền huyễn)", input: "xuanhuan", script: "genrecontent.js" },
        { title: "\u4fee\u771f (Tu chân)",     input: "xiuzhen",  script: "genrecontent.js" },
        { title: "\u90fd\u5e02 (Đô thị)",     input: "dushi",    script: "genrecontent.js" },
        { title: "\u6b77\u53f2 (Lịch sử)",    input: "lishi",    script: "genrecontent.js" },
        { title: "\u7db2\u904a (Game)",        input: "wangyou",  script: "genrecontent.js" },
        { title: "\u79d1\u5e7b (Khoa huyễn)", input: "kehuan",   script: "genrecontent.js" },
        { title: "\u5973\u983b (Nữ phái)",    input: "nvpin",    script: "genrecontent.js" },
        { title: "\u9748\u7570 (Linh dị)",    input: "lingyi",   script: "genrecontent.js" },
        { title: "\u540c\u4eba (Fan fiction)", input: "tongren",  script: "genrecontent.js" },
        { title: "\u8ecd\u4e8b (Quân sự)",    input: "junshi",   script: "genrecontent.js" },
        { title: "\u61f8\u7591 (Huyền nghi)", input: "xuanyi",   script: "genrecontent.js" },
        { title: "\u7a7f\u8d8a (Xuyên không)", input: "chuanyue", script: "genrecontent.js" },
        { title: "\u5176\u5b83 (Khác)",       input: "other",    script: "genrecontent.js" }
    ]);
}