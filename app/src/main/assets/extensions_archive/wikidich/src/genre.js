load('config.js');

function execute() {
    let doc = getDoc(BASE_URL + "/");
    if (!doc) return Response.success([
        {title: "Tien hiep", input: "/the-loai/tien-hiep", script: "gen.js"},
        {title: "Ngon tinh", input: "/the-loai/ngon-tinh", script: "gen.js"},
        {title: "Truyen full", input: "/danh-sach/truyen-full", script: "gen.js"},
        {title: "Truyen hot", input: "/danh-sach/truyen-hot", script: "gen.js"}
    ]);

    let data = [];
    let seen = {};
    doc.select("a[href^='/the-loai/'], a[href^='/danh-sach/']").forEach(function(a) {
        let title = cleanText(a.text());
        let href = a.attr("href");
        if (!title || !href || seen[href]) return;
        seen[href] = true;
        data.push({title: title, input: normalizeUrl(href), script: "gen.js"});
    });
    return Response.success(data);
}
