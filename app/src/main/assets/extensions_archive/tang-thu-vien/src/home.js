load("config.js");

function execute() {
    return Response.success([
        { title: "Mới cập nhật", input: "/tong-hop", script: "gen.js" },
        { title: "Truyện mới", input: "/tong-hop?ord=new", script: "gen.js" },
        { title: "Hoàn thành", input: "/tong-hop?fns=ht", script: "gen.js" },
        { title: "Đề cử", input: "/tong-hop?rank=nm&time=m", script: "gen.js" },
        { title: "Xem nhiều", input: "/tong-hop?rank=vw&time=m", script: "gen.js" },
        { title: "Yêu thích", input: "/tong-hop?rank=yt", script: "gen.js" },
        { title: "Theo dõi", input: "/tong-hop?rank=td", script: "gen.js" },
    ]);
}
