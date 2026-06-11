load('config.js');

function execute() {
    return Response.success([
        {title: "Mới cập nhật", input: BASE_URL + "/truyen/?m_orderby=latest", script: "gen.js"},
        {title: "Xem nhiều", input: BASE_URL + "/truyen/?m_orderby=views", script: "gen.js"},
        {title: "Truyện Full", input: BASE_URL + "/truyen/?m_orderby=trending&status=end&page=1", script: "gen.js"},
        {title: "Đang hot", input: BASE_URL + "/truyen/?m_orderby=trending", script: "gen.js"}
    ]);
}
