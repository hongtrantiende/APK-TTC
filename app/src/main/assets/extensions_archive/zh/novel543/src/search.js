load("config.js");

function execute(key, page) {
    var p = page ? parseInt(page) : 1;
    var doc = null;
    var items = null;

    // Strategy 1: Try fast direct fetch with cached cookies (fastest)
    var urls = [
        BASE_URL + "/search/" + encodeURIComponent(key) + "/",
        BASE_URL + "/search/?keyword=" + encodeURIComponent(key),
        BASE_URL + "/bookstack/?keyword=" + encodeURIComponent(key)
    ];

    for (var i = 0; i < urls.length; i++) {
        var testUrl = urls[i];
        if (p > 1) {
            testUrl += (testUrl.indexOf("?") >= 0 ? "&" : "?") + "page=" + p;
        }
        doc = fetchFast(testUrl);
        if (doc) {
            items = parseList(doc);
            if (items && items.length > 0) break;
        }
    }

    // Strategy 2: Browser fetch with each URL pattern
    if (!items || items.length === 0) {
        for (var j = 0; j < urls.length; j++) {
            var testUrl2 = urls[j];
            if (p > 1) {
                testUrl2 += (testUrl2.indexOf("?") >= 0 ? "&" : "?") + "page=" + p;
            }
            doc = fetchBrowserCF(testUrl2, 12000);
            if (doc) {
                items = parseList(doc);
                if (items && items.length > 0) break;
            }
        }
    }

    // Strategy 3: Browser JS search form submission (slowest, last resort)
    if (!items || items.length === 0) {
        var browser = Engine.newBrowser();
        browser.setUserAgent(UserAgent.android());
        browser.block([
            "holmesmind.com", "tamedia.com", "popin.cc",
            "pubfuture", "stat.gn01.top", "beacon.min.js",
            ".jpg", ".jpeg", ".png", ".webp", ".gif",
            ".css", ".woff", ".woff2"
        ]);
        doc = browser.launch(BASE_URL + "/", 12000);
        if (doc) {
            var safeKey = key.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
            var js = "var inp=document.querySelector('.search-input');" +
                "if(inp){inp.value='" + safeKey + "';" +
                "var f=inp.closest('.search-form');" +
                "if(f){var b=f.querySelector('button');if(b)b.click();}}";
            browser.callJs(js, 3000);
            sleep(2000);
            doc = browser.html();
            if (doc) items = parseList(doc);
        }
        browser.close();
    }

    if (!items || items.length === 0) return Response.success([], null);

    var next = getNextPage(doc, p);
    return Response.success(items, next);
}
