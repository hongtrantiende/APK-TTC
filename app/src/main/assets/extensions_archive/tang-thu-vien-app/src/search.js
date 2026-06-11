load("config.js");
function execute(key, page) {
    sleep(100);
    const apiBase = "https://ttv-story-search.datbi2k.workers.dev";
    if (!key) return null;

    let response = fetch(apiBase + "/search?q=" + encodeURIComponent(key));
    if (!response.ok) return null;

    let data = response.json();
    if (!data || !data.length) return Response.success([], "");

    let novels = [];
    data.forEach(item => {
        novels.push({
            name: item.name,
            link: item.id + "",
            description: item.author || "",
            cover: "",
            host: BASE_URL
        });
    });

    return Response.success(novels, "");
}
