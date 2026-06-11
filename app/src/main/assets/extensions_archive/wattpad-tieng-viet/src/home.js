load("config.js");

function execute() {
  return Response.success([
    {
      title: "Nổi bật",
      script: "homecontent.js",
      input: API_V4 + "/stories?filter=hot&language=" + LANG_VI,
    },
    {
      title: "Mới cập nhật",
      script: "homecontent.js",
      input: API_V4 + "/stories?filter=new&language=" + LANG_VI,
    },
    {
      title: "Mới nhất",
      script: "homecontent.js",
      input: API_V4 + "/stories?filter=fresh&language=" + LANG_VI,
    },
    {
      title: "Hoàn thành",
      script: "homecontent.js",
      input: API_V4 + "/stories?filter=hot&completed=1&language=" + LANG_VI,
    },
  ]);
}
