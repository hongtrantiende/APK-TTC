load("config.js");

function execute() {
    return Response.success([
        {title: "Huyền huyễn", input: "api://stories?cat=huyen-huyen", script: "gen.js"},
        {title: "Tiên hiệp", input: "api://stories?cat=tien-hiep", script: "gen.js"},
        {title: "Đô thị", input: "api://stories?cat=do-thi", script: "gen.js"},
        {title: "Khoa huyễn", input: "api://stories?cat=khoa-huyen", script: "gen.js"},
        {title: "Kiếm hiệp", input: "api://stories?cat=kien-hiep", script: "gen.js"},
        {title: "Dị năng", input: "api://stories?cat=di-nang", script: "gen.js"},
        {title: "Quân sự", input: "api://stories?cat=quan-su", script: "gen.js"},
        {title: "Lịch sử", input: "api://stories?cat=lich-su", script: "gen.js"},
        {title: "Võng du", input: "api://stories?cat=vong-du", script: "gen.js"},
        {title: "Đồng nhân", input: "api://stories?cat=dong-nhan", script: "gen.js"},
        {title: "Linh dị", input: "api://stories?cat=linh-di", script: "gen.js"},
        {title: "Điềm văn", input: "api://stories?cat=dien-van", script: "gen.js"},
        {title: "Hiện đại", input: "api://stories?cat=hien-dai", script: "gen.js"},
        {title: "Cổ đại", input: "api://stories?cat=co-dai", script: "gen.js"},
        {title: "Ngôn tình", input: "api://stories?cat=ngon-tinh", script: "gen.js"},
        {title: "Trọng sinh", input: "api://stories?cat=trong-sinh", script: "gen.js"},
        {title: "Xuyên không", input: "api://stories?cat=xuyen-khong", script: "gen.js"},
        {title: "Cạnh kỹ", input: "api://stories?cat=canh-ky", script: "gen.js"},
        {title: "Tiên hiệp", input: "api://stories?cat=tien-hiep", script: "gen.js"},
        {title: "Khác", input: "api://stories?cat=khac", script: "gen.js"}
    ]);
}