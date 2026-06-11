load('config.js');

function execute(input, next) {
    var page = next || "1";
    var url = input.replace("{{page}}", page);
    var res = fetch(url, {
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "Referer": BASE_URL
        }
    });
    if (!res.ok) return Response.error("Cannot load comments: " + res.status);

    var json = parseJson(res.text() + "");
    if (!json) return Response.error("Cannot parse comments");
    if (json.result && json.result !== "success") return Response.error(json.msg || "Cannot load comments");

    var list = json.data || [];
    var data = [];
    for (var i = 0; i < list.length; i++) {
        var item = list[i];
        var content = buildContent(item);
        if (!content) continue;
        data.push({
            name: cleanText(item.nickName || item.loginName || "匿名"),
            content: content,
            description: buildDescription(item)
        });
    }

    var currentPage = parseInt(json.currentPage || page, 10);
    var totalPage = parseInt(json.totalPage || 0, 10);
    var nextPage = totalPage > currentPage ? String(currentPage + 1) : null;
    return Response.success(data, nextPage);
}

function parseJson(text) {
    try {
        return JSON.parse(text);
    } catch (e) {
        return null;
    }
}

function cleanText(text) {
    return ((text || "") + "").replace(/\s+/g, " ").trim();
}

function escapeHtml(text) {
    text = (text || "") + "";
    return text.replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\"/g, "&quot;")
        .replace(/'/g, "&#39;");
}

function formatContent(text) {
    text = escapeHtml(cleanText(text));
    return text.replace(/\r?\n/g, "<br>");
}

function buildContent(item) {
    var parts = [];
    var content = formatContent(item.content || "");
    if (content) parts.push(content);
    if (item.imageUrl) parts.push('<img src="' + escapeHtml(item.imageUrl) + '">');

    var children = item.children || [];
    for (var i = 0; i < children.length; i++) {
        var child = children[i];
        var childContent = formatContent(child.content || "");
        if (!childContent) continue;
        var childName = cleanText(child.nickName || child.loginName || "匿名");
        var replyName = cleanText(child.replyNickName || "");
        var prefix = childName;
        if (replyName) prefix += " 回复 " + replyName;
        parts.push('<br><small>' + escapeHtml(prefix) + '：' + childContent + '</small>');
        if (child.imageUrl) parts.push('<br><img src="' + escapeHtml(child.imageUrl) + '">');
    }

    return parts.join("");
}

function buildDescription(item) {
    var parts = [];
    var time = cleanText(item.createTimeFormat || item.createTime || "");
    if (time) parts.push(time);
    if (item.score !== undefined && item.score !== null && item.score !== 0) parts.push("评分: " + item.score);
    if (item.awesome !== undefined && item.awesome !== null) parts.push("赞: " + item.awesome);
    if (item.replyCount !== undefined && item.replyCount !== null && item.replyCount > 0) parts.push("回复: " + item.replyCount);
    return parts.join(" · ");
}
