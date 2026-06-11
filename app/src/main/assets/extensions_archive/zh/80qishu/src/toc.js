load('config.js');

function execute(url) {
    let storyUrl = storyUrlFromAny(url);
    let txtUrls = resolveTxtUrls(storyUrl);
    if (txtUrls.length === 0) return Response.error("未找到 TXT 下载链接。");

    let text = "";
    let txtUrl = "";
    for (let i = 0; i < txtUrls.length; i++) {
        text = getText(txtUrls[i], storyUrl);
        if (cleanText(text).length > 100) {
            txtUrl = txtUrls[i];
            break;
        }
    }
    if (!txtUrl || !text) return loadError();

    let chapters = splitChapters(text);
    let data = [];
    for (let j = 0; j < chapters.length; j++) {
        data.push({
            name: tocName(chapters[j], j),
            url: readerUrl(txtUrl, storyUrl, j),
            host: BASE_URL
        });
    }
    return Response.success(data);
}
