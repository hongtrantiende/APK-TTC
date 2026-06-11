load('config.js');

function execute(url) {
    return Response.success([storyUrlFromAny(url)]);
}
