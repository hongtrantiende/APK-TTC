load('config.js');
function execute(input, next) {
    if (!input) return null;

    let page = next ? next : "0";
    let payload = {
        id_of_story: String(input),
        delta: String(page),
        type: "0",
        user_id: "0"
    };

    let response = fetch(API_BASE + "/ttv/ttv_apiv2/public/get_comment", {
        method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded"},
        body: "get_comment=" + encodeURIComponent(JSON.stringify(payload))
    });

    if (!response.ok) return null;

    let data = response.json();
    let list = (data && data.comments) ? data.comments : [];
    let comments = [];

    list.forEach(item => {
        comments.push({
            name: item.full_name || "",
            content: item.content || "",
            description: item.time_post || ""
        });
    });

    let more = list.length ? (parseInt(page, 10) + 1).toString() : "";
    return Response.success(comments, more);
}
