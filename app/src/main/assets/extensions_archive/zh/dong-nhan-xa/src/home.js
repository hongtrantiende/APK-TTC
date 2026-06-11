function execute() {
    return Response.success([
        {title: "全本同人", input:  "https://tongrenshe.cc/tags-150", script: "gen.js"},
        {title: "连载同人", input:  "https://tongrenshe.cc/tags-151", script: "gen.js"},
    ]);
}