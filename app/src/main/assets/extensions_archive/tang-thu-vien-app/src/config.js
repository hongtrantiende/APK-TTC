const BASE_URL = "https://apptangthuvien.net";
const API_BASE = "https://nae.vn";
const IMAGE_BASE = "https://www.nae.vn/ttv/ttv/public/images/story/";

//504942
const userId_ = (() => {
    let raw = typeof id !== "undefined" ? id : "";

    raw = String(raw).replace(/"/g, "").trim();
    return raw || "";
})();