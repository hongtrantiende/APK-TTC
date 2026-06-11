load("config.js");
function execute(url) {
  let response = fetch(url);
  if (response.ok) {
    let doc = response.html();
    let htm = doc.select("#content-section pre").html();
    return Response.success(htm);
  }
  return null;
}
