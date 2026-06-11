load("config.js");

function execute() {
  return Response.success([
    {
      title: "Truyện đọc nhiều",
      input: BASE_URL + "/danh-muc/bang-xep-hang",
      script: "listWithoutPagination.js",
    },
    {
      title: "Truyện đã hoàn thành",
      input: BASE_URL + "/danh-muc/truyen-da-hoan-thanh",
      script: "listWithPagination.js",
    },
    {
      title: "Truyện dịch",
      input: BASE_URL + "/danh-muc/truyen-dich",
      script: "listWithPagination.js",
    },
    {
      title: "Truyện trả phí",
      input: BASE_URL + "/danh-muc/truyen-tra-phi",
      script: "listWithPagination.js",
    },
    {
      title: "Truyện convert",
      input: BASE_URL + "/danh-muc/truyen-convert",
      script: "listWithPagination.js",
    },
    {
      title: "Truyện sáng tác",
      input: BASE_URL + "/danh-muc/truyen-sang-tac",
      script: "listWithPagination.js",
    },
    {
      title: "Truyện đề cử",
      input: BASE_URL + "/danh-muc/truyen-de-cu",
      script: "listWithoutPagination.js",
    },
  ]);
}
