function fetch(url, options) {
    return _HttpResponse(__(["Http_fetch", url, JSON.stringify(options)]))
}

var Response = {
    success: function (data, data2) {
        return JSON.stringify({
            "code": 0,
            "data": data,
            "data2": data2,
        });
    },
    error: function (data) {
        return JSON.stringify({
            "code": 1,
            "data": data,
        });
    }
}

function _Blob(base64, type) {
    let data = base64 || "";
    let contentType = type || "";
    let padding = 0;
    if (data.endsWith("==")) {
        padding = 2;
    } else if (data.endsWith("=")) {
        padding = 1;
    }
    return {
        _isBlob: true,
        _base64: data,
        type: contentType,
        size: Math.max(0, Math.floor(data.length * 3 / 4) - padding),
        base64: function () {
            return data;
        },
        toString: function () {
            return data;
        }
    }
}

var Blob = {
    fromBase64: function (base64, type) {
        return _Blob(base64, type);
    }
}

function _HttpResponse(response) {
    return {
        get request() {
            return _HttpRequest(response);
        },
        get headers() {
            return JSON.parse(__(["HttpResponse_headers", response]));
        },
        header: function (key) {
            return __(["HttpResponse_header", response, key]);
        },
        get status() {
            return __(["HttpResponse_status", response]);
        },
        get statusText() {
            return __(["HttpResponse_statusText", response]);
        },
        get ok() {
            return __(["HttpResponse_ok", response]);
        },
        get url() {
            return __(["HttpResponse_url", response]);
        },
        html: function (charset) {
            let text = __(["HttpResponse_text", response, charset]);
            return _HtmlElement(__(["HtmlDocument_parse", text]));
        },
        text: function (charset) {
            return __(["HttpResponse_text", response, charset]);
        },
        base64: function (charset) {
            return __(["HttpResponse_base64", response, charset]);
        },
        blob: function () {
            return _Blob(
                __(["HttpResponse_base64", response]),
                __(["HttpResponse_header", response, "content-type"])
            );
        },
        json: function () {
            return JSON.parse(__(["HttpResponse_text", response]));
        }
    }
}

function _HttpRequest(response) {
    return {
        headers: JSON.parse(__(["HttpRequest_headers", response])),
        url: __(["HttpRequest_url", response]),
    }
}

function _HtmlElement(element) {
    return {
        select: function (query) {
            return _HtmlElements(__(["HtmlElement_select", element, query]));
        },
        attr: function (attribute) {
            return __(["HtmlElement_attr", element, attribute])
        },
        text: function () {
            return __(["HtmlElement_text", element]);
        },
        html: function () {
            return __(["HtmlElement_html", element]);
        },
        remove: function () {
            return __(["HtmlElement_remove", element]);
        },
        attributes: function () {
            return JSON.parse(__(["HtmlElement_attributes", element]));
        },
        toString: function () {
            return __(["HtmlElement_html", element]);
        },
    }
}

function _HtmlElements(elements) {
    return {
        length: __(["HtmlElements_size", elements]),
        size: function () {
            return __(["HtmlElements_size", elements]);
        },
        isEmpty: function () {
            return __(["HtmlElements_size", elements]) == 0;
        },
        forEach: function (callback) {
            let length = __(["HtmlElements_size", elements]);
            for (let i = 0; i < length; i++) {
                callback(_HtmlElement(__(["HtmlElements_get", elements, i])));
            }
        },
        map: function (callback) {
            let arr = [];
            let length = __(["HtmlElements_size", elements]);
            for (let i = 0; i < length; i++) {
                arr.push(callback(_HtmlElement(__(["HtmlElements_get", elements, i]))));
            }
            return arr;
        },
        get: function (index) {
            return _HtmlElement(__(["HtmlElements_get", elements, index]));
        },
        first: function () {
            return _HtmlElement(__(["HtmlElements_first", elements]));
        },
        last: function () {
            return _HtmlElement(__(["HtmlElements_last", elements]));
        },
        select: function (query) {
            return _HtmlElements(__(["HtmlElements_select", elements, query]));
        },
        attr: function (attribute) {
            return __(["HtmlElements_attr", elements, attribute])
        },
        text: function () {
            return __(["HtmlElements_text", elements]);
        },
        html: function () {
            return __(["HtmlElements_html", elements]);
        },
        remove: function () {
            return __(["HtmlElements_remove", elements]);
        },
        toString: function () {
            return __(["HtmlElements_html", elements]);
        },
    }
}

var Html = {
    parse: function (text) {
        return _HtmlElement(__(["HtmlDocument_parse", text]));
    }
}

var Log = {
    log: function (message) {
        return __(["Log_log", message]);
    }
}

var Qt = {
    translate: function (text, to, extras) {
        let raw = __(["Qt_translate", text, to, extras ? JSON.stringify(extras) : ""]);
        if (raw == null) return null;
        try {
            return JSON.parse(raw);
        } catch (e) {
            return null;
        }
    }
}

var Console = {
    log: function (message) {
        return __(["Log_log", message]);
    }
}

var console = Console;

var Script = {
    execute: function (script, name, input) {
        return __(["Script_execute", script, name, input]);
    },
}

var Engine = {
    newBrowser: function () {
        let nativeBrowser = __(["Engine_newBrowser"]);
        let browser = _Browser(nativeBrowser);
        browser.setUserAgent(UserAgent.system());
        return browser;
    }
}

var Graphics = {
    createCanvas: function (width, height) {
        let canvas = __(["Graphics2D_createCanvas", width, height]);
        return _Canvas(canvas);
    },
    createImage: function (base64) {
        let image = __(["Graphics2D_createImage", base64]);
        return _Image(image);
    }
}

function _Canvas(canvas) {
    return {
        drawImage: function () {
            switch (arguments.length) {
                case 3:
                    __(["Graphics2D_drawImage", canvas, arguments[0].image, arguments[1], arguments[2]]);
                    break;
                case 6:
                    __(["Graphics2D_drawImage", canvas, arguments[0].image, arguments[1], arguments[2], arguments[3], arguments[4], arguments[5]]);
                    break;
                case 9:
                    __(["Graphics2D_drawImage", canvas, arguments[0].image, arguments[1], arguments[2], arguments[3], arguments[4], arguments[5], arguments[6], arguments[7], arguments[8]]);
                    break;
            }
        },

        capture: function () {
            let image = __(["Graphics2D_capture", canvas]);
            return image;
        }
    }
}

function _Image(image) {
    return {
        image: image,
        width: __(["Graphics2D_imageWidth", image]),
        height: __(["Graphics2D_imageHeight", image])
    }
}

function _Browser(browser) {
    return {
        launch: function (url, timeout) {
            let html = __(["Browser_launch", browser, url, timeout]);
            return Html.parse(html)
        },

        launchAsync: function (url) {
            __(["Browser_launchAsync", browser, url]);
        },

        loadHtml: function (baseUrl, html) {
            __(["Browser_loadHtml", browser, baseUrl, html]);
        },

        getVariable: function (name) {
            return __(["Browser_getVariable", browser, name]);
        },

        callJs: function (script, timeout) {
            return Html.parse(__(["Browser_callJs", browser, script, timeout]));
        },

        block: function (urls) {
            __(["Browser_block", browser, JSON.stringify(urls)]);
        },

        urls: function () {
            return JSON.parse(__(["Browser_urls", browser]));
        },

        waitUrl: function (urls, timeout) {
            let patterns = Array.isArray(urls) ? JSON.stringify(urls) : urls;
            __(["Browser_waitUrl", browser, patterns, timeout]);
        },

        setUserAgent: function (userAgent) {
            __(["Browser_setUserAgent", browser, userAgent]);
        },

        html: function (timeout) {
            let html = __(["Browser_html", browser, timeout]);
            return Html.parse(html);
        },

        close: function () {
            __(["Browser_close", browser]);
        }
    }
}

var Http = {
    get: function (url) {
        return _HttpGet(url);
    },
    post: function (url) {
        return _HttpPost(url);
    }
}

function _HttpGet(url) {
    var options = {};
    options['method'] = "GET";
    return {
        headers: function (data) {
            options['headers'] = data;
            return this;
        },
        params: function (data) {
            options['queries'] = data;
            return this;
        },
        queries: function (data) {
            options['queries'] = data;
            return this;
        },
        timeout: function (timeout) {
            options['timeout'] = timeout;
            return this;
        },
        string: function (charset) {
            return fetch(url, options).text(charset)
        },
        blob: function () {
            return fetch(url, options).blob()
        },
        html: function (charset) {
            return fetch(url, options).html(charset)
        },
        url: function () {
            return url;
        }
    }
}

function _HttpPost(url) {
    var options = {};
    options['method'] = "POST";
    return {
        headers: function (data) {
            options['headers'] = data;
            return this;
        },
        queries: function (data) {
            options['queries'] = data;
            return this;
        },
        params: function (data) {
            options['queries'] = data;
            return this;
        },
        body: function (data) {
            options['body'] = data;
            return this;
        },
        binary: function (data, type) {
            options['body'] = Blob.fromBase64(data, type);
            return this;
        },
        timeout: function (timeout) {
            options['timeout'] = timeout;
            return this;
        },
        string: function (charset) {
            return fetch(url, options).text(charset);
        },
        blob: function () {
            return fetch(url, options).blob();
        },
        html: function (charset) {
            return fetch(url, options).html(charset);
        },
        url: function () {
            return url;
        }
    }
}

var UserAgent = {
    system: function () {
        return __(["UserAgent_system"]);
    },
    chrome: function () {
        return __(["UserAgent_chrome"]);
    },
    android: function () {
        return __(["UserAgent_android"]);
    },
    ios: function () {
        return __(["UserAgent_ios"]);
    }
}

var localStorage = {
    setItem: function (key, value) {
        return __(["LocalStorage_setItem", key, value]);
    },
    getItem: function (key) {
        return __(["LocalStorage_getItem", key]);
    },
    removeItem: function (key) {
        return __(["LocalStorage_removeItem", key]);
    },
    clear: function () {
        __(["LocalStorage_clear"]);
    }
}

var cacheStorage = {
    setItem: function (key, value) {
        return __(["CacheStorage_setItem", key, value]);
    },
    getItem: function (key) {
        return __(["CacheStorage_getItem", key]);
    },
    removeItem: function (key) {
        return __(["CacheStorage_removeItem", key]);
    },
    clear: function () {
        return __(["CacheStorage_clear"]);
    }
}

var localCookie = {
    setCookie: function (value) {
        return __(["Cookie_set", value]);
    },
    getCookie: function () {
        return __(["Cookie_get"]);
    }
}

var localConfig = {
    getItem: function (key) {
        return __(["LocalConfig_getItem", key]);
    }
}

function WebSocket(url, headers) {
    let wsSocket = __(["WebSocket_create", url, JSON.stringify(headers)]);
    let socket = {
        CONNECTING: 0,
        OPEN: 1,
        CLOSING: 2,
        CLOSED: 3,
        url: url,
        headers: headers || {},
        readyState: 0,
        connect: function () {
            __(["WebSocket_connect", wsSocket]);
            socket.readyState = socket.OPEN;
            return socket;
        },
        message: function () {
            return _WebSocketFrame(__(["WebSocket_message", wsSocket]));
        },
        receive: function () {
            return socket.message();
        },
        send: function (data) {
            if (typeof data === "string") {
                return socket.sendText(data);
            }
            return socket.sendBuffer(data);
        },
        sendText: function (message) {
            return __(["WebSocket_send_text", wsSocket, message]);
        },
        sendBuffer: function (data) {
            return __(["WebSocket_send_buffer", wsSocket, data]);
        },
        close: function () {
            socket.readyState = socket.CLOSING;
            let result = __(["WebSocket_close", wsSocket]);
            socket.readyState = socket.CLOSED;
            return result;
        }
    };
    return socket;
}

function _WebSocketFrame(frame) {
    return {
        type: __(["WebSocket_frame_type", frame]),
        data: __(["WebSocket_frame_data", frame])
    }
}

function sleep(time) {
    __(["Common_sleep", time]);
}

function __(data) {
    return JSBridge.execFunction(data);
}
