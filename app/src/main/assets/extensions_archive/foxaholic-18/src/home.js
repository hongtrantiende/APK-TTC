load("config.js");

function execute() {
    return Response.success([
        { title: "Novel", input: "novel", script: "homecontent.js" }
    ]);
}