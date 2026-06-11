load('config.js');

function execute(url) {
    url = url.replace(/^(?:https?:\/\/)?(?:[^@\n]+@)?(?:www\.)?([^:\/\n?]+)/img, BASE_URL);

    let response = fetch(url);
    if (response.ok) {
        let doc = response.html();
        let chapters = [];
        doc.select("#list-chapterAll dd a").forEach(e => {
            chapters.push({
                name: e.text().replace("••","").replace(/^(\d+).第/,'第').replace(/^(正文|VIP章节|最新章节)?(\s+|_)|[\(\{（｛【].*[求更谢乐发推票盟补加字Kk\/].*[\)\}）｝】]/g,'').replace(/^(\d+)[、．]第.+章/,'第$1章').replace(/^(\d+)、\d+、/,'第$1章 ').replace(/^(\d+)、\d+/,'第$1章').replace(/^(\d+)、/,'第$1章 ').replace(/^(第.+章)\s?第.+章/,'$1').replace(/\(.+\)/,'').replace(/\[|。/,''),
                url: e.attr("href"),
                host: BASE_URL
            })
        });

        return Response.success(chapters);
    }

    return null;
}