load("config.js");
function execute(url) {
    const apiDetail = API_BASE + "/ttv/ttv_apiv2/public/get_list_story_author";

    let storyId = url.match(/\d+/)[0];
    let response = fetch(apiDetail, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: "id_story=" + storyId
    });

    if (!response.ok) return null;

    let data = response.json();
    if (!data || data.status !== 1 || !data.story) return null;

    let story = data.story;
    let storiesAuthor = data.stories_author || [];
    let cover = story.image ? (IMAGE_BASE + story.image + ".jpg") : "";
    let detailParts = [];
    if (story.category_name) detailParts.push("Thể Loại: " + story.category_name);
    if (story.count_chapter != null) detailParts.push("Số Chương: " + story.count_chapter);
    if (story.time_fix) detailParts.push("Cập nhật: " + story.time_fix);

    let suggests = [];
    if (storiesAuthor.length) {
        suggests = [
            {
                title: "Truyện cùng tác giả",
                input: JSON.stringify(storiesAuthor),
                script: "suggests.js"
            }
        ];
    }

    return Response.success({
        name: story.name,
        cover: cover,
        author: story.author,
        description: detailParts.join("<br>") + "<br>" + story.introduce,
        detail: detailParts.join("<br>"),
        suggests: suggests,
        comment: {
            input: story.id + "",
            script: "comment.js"
        },
        ongoing: story.finish === 0
    });
}
