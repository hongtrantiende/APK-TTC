function execute() {
    return Response.success([
        { title: "更新列表", input: "https://www.wfxs.tw/toppostdate/1.html", script: "source.js" },
        { title: "排行", input: "https://www.wfxs.tw/topallvisit/1.html", script: "source.js" },
    ]);
}