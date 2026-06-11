function execute() {
    return Response.success([
        { title: "[全部]玄幻", input: "/h5/category-rank-category_id-1-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]武侠", input: "/h5/category-rank-category_id-2-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]都市", input: "/h5/category-rank-category_id-3-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]历史", input: "/h5/category-rank-category_id-4-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]科幻", input: "/h5/category-rank-category_id-5-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]网游", input: "/h5/category-rank-category_id-6-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]女生", input: "/h5/category-rank-category_id-7-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]奇幻", input: "/h5/category-rank-category_id-8-rank_type-xinshu", script: "cate.js" },
        { title: "[全部]同人", input: "/h5/category-rank-category_id-66-rank_type-xinshu", script: "cate.js" },
        { title: "最热榜", input: "/h5/category-rank2-rank_type-zuire", script: "cate.js" },
        { title: "完结榜", input: "/h5/category-rank2-rank_type-over", script: "cate.js" },
        { title: "推荐榜", input: "/h5/category-rank2-rank_type-tuijian", script: "cate.js" },
        { title: "新书榜", input: "/h5/category-rank2-rank_type-xinshu", script: "cate.js" },
        { title: "评分榜", input: "/h5/category-rank2-rank_type-pinfen", script: "cate.js" },
    ]);
}
