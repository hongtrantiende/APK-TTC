function execute(url) {
    let response = fetch(url);

    if (response.ok) {
        let doc = response.html();
        if (doc.select(".protect").length > 0) {
            let html = doc.html();
            // Inject attachShadow
            html = html.replace("</head>", "<script>Element.prototype._attachShadow=Element.prototype.attachShadow,Element.prototype.attachShadow=function(){return this._attachShadow({mode:'open'})};</script></head>")
            let browser = Engine.newBrowser();
            // Load html content injected
            browser.loadHtml(url, html);
            for (let i = 0; i < 3; i++) {
                sleep(1000);
                let htm = browser.html();
                if (htm.select(".protect").text().length < 100) {
                    break;
                }
            }
            // Update html content in DOM Shadow
            browser.callJs("let protects=document.getElementsByClassName('protect');for(let i=0;i<protects.length;i++)protects[i].innerHTML=protects[i].shadowRoot.innerHTML;", 200);
            doc = browser.html();
            browser.close()
        }
        let content = doc.select(".book-list.full-story.content").first().html();
        content = content.replace(/&nbsp;/g, "").trim();
        return Response.success(content);
    }
    return null;
}