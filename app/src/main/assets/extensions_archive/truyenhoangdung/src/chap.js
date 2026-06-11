function execute(url) {
  const response = fetch(url);

  if (response.ok) {
    return Response.success(response.html().select("#noidung").html());
  }

  return null;
}
