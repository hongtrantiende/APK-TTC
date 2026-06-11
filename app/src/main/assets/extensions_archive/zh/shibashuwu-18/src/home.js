load("config.js");

function execute() {
    var tabs = [];
    for (var i = 0; i < CATEGORY_TABS.length; i++) {
        tabs.push({
            title: CATEGORY_TABS[i].title,
            input: CATEGORY_TABS[i].input,
            script: "homecontent.js"
        });
    }
    return Response.success(tabs);
}
