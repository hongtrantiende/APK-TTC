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

// ─── Legacy VBook API Polyfills ─────────────────────────────────────────────
// Các API cũ từ VBook phiên bản trước — một số extension vẫn dùng

/**
 * $.QA(element, selector) — query all elements, trả về HtmlElements
 * $.Q(element, selector)  — query first element, trả về HtmlElement
 */
var $ = {
    QA: function(element, selector) {
        if (!element || !selector) return { length: 0, size: function(){return 0;}, forEach: function(){}, map: function(){return[];}, get: function(){return null;}, first: function(){return null;} };
        return element.select(selector);
    },
    Q: function(element, selector) {
        if (!element || !selector) return { text: function(){return '';}, attr: function(){return '';}, html: function(){return '';}, select: function(){return $._empty();} };
        var els = element.select(selector);
        if (!els || els.length === 0) return { text: function(){return '';}, attr: function(){return '';}, html: function(){return '';}, select: function(){return $._empty();} };
        return els.first();
    },
    _empty: function() {
        return { length: 0, size: function(){return 0;}, forEach: function(){}, map: function(){return[];}, first: function(){return null;} };
    }
};

/**
 * String.format(template, ...args) — Java-style string format
 * Hỗ trợ: %s, %d, %f, {0} {1}...
 */
if (!String.format) {
    String.format = function(template) {
        var args = Array.prototype.slice.call(arguments, 1);
        return template
            .replace(/\{(\d+)\}/g, function(m, i) { return args[i] !== undefined ? args[i] : m; })
            .replace(/%s/g, function() { return args.shift !== undefined ? String(args.shift() || '') : '%s'; })
            .replace(/%d/g, function() { return args.shift !== undefined ? parseInt(args.shift() || 0) : '%d'; });
    };
}

/**
 * log(msg) — standalone log function (shortcut cho Console.log)
 */
function log(msg) {
    return __(['Log_log', String(msg)]);
}

// ─── Modern JS Polyfills for Rhino ──────────────────────────────────────────
// Rhino không có nhiều browser API — cần polyfill để extension chạy được

/**
 * XMLHttpRequest — sync XHR dùng fetch() bridge
 * 19 extension files dùng XHR. Map sang fetch() của VBook.
 */
if (typeof XMLHttpRequest === 'undefined') {
    function XMLHttpRequest() {
        this._method = 'GET';
        this._url = '';
        this._headers = {};
        this._body = null;
        this._response = null;
        this.status = 0;
        this.statusText = '';
        this.responseText = '';
        this.responseURL = '';
        this.readyState = 0;
        this.onreadystatechange = null;
        this.onload = null;
        this.onerror = null;
    }
    XMLHttpRequest.prototype.open = function(method, url, async) {
        this._method = method || 'GET';
        this._url = url || '';
        this.readyState = 1;
    };
    XMLHttpRequest.prototype.setRequestHeader = function(key, value) {
        this._headers[key] = value;
    };
    XMLHttpRequest.prototype.getResponseHeader = function(key) {
        if (!this._response) return null;
        try { return this._response.header(key); } catch(e) { return null; }
    };
    XMLHttpRequest.prototype.send = function(body) {
        this._body = body || null;
        var opts = {
            method: this._method,
            headers: this._headers
        };
        if (body) {
            if (typeof body === 'string') opts.body = body;
        }
        try {
            this._response = fetch(this._url, opts);
            this.status = this._response.status || 200;
            this.statusText = this._response.statusText || 'OK';
            this.responseText = this._response.text();
            this.responseURL = this._url;
            this.readyState = 4;
            if (this.onreadystatechange) this.onreadystatechange();
            if (this.onload) this.onload();
        } catch(e) {
            this.status = 0;
            this.statusText = 'Error';
            this.readyState = 4;
            if (this.onerror) this.onerror(e);
        }
    };
    XMLHttpRequest.prototype.abort = function() {};
}

/**
 * atob / btoa — Base64 encode/decode
 * Extension dùng để encode/decode data
 */
if (typeof atob === 'undefined') {
    var _b64chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
    function btoa(input) {
        var str = String(input), output = '';
        for (var block, charCode, idx = 0, map = _b64chars;
             str.charAt(idx | 0) || (map = '=', idx % 1);
             output += map.charAt(63 & block >> 8 - idx % 1 * 8)) {
            charCode = str.charCodeAt(idx += 3/4);
            block = block << 8 | charCode;
        }
        return output;
    }
    function atob(input) {
        var str = String(input).replace(/[=]+$/, '');
        var output = '';
        for (var bc = 0, bs, buffer, idx = 0;
             buffer = str.charAt(idx++);
             ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer,
             bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
            buffer = _b64chars.indexOf(buffer);
        }
        return output;
    }
}

/**
 * URL class polyfill — parse URLs
 * 1 extension dùng new URL(...)
 */
if (typeof URL === 'undefined') {
    function URL(href, base) {
        var url = String(href || '');
        if (base && !url.match(/^https?:\/\//)) {
            var b = String(base).replace(/\/$/, '');
            url = url.startsWith('/') ? b.replace(/^(https?:\/\/[^\/]+).*/, '$1') + url : b + '/' + url;
        }
        var m = url.match(/^(https?):\/\/([^\/\?#]*)([^?#]*)(\?[^#]*)?(#.*)?$/);
        this.href = url;
        this.protocol = m ? m[1] + ':' : '';
        this.hostname = m ? m[2].replace(/:\d+$/, '') : '';
        this.port = m ? (m[2].match(/:(\d+)$/) || ['', ''])[1] : '';
        this.host = m ? m[2] : '';
        this.pathname = m ? (m[3] || '/') : '/';
        this.search = m ? (m[4] || '') : '';
        this.hash = m ? (m[5] || '') : '';
        this.origin = this.protocol + '//' + this.host;
        this.toString = function() { return this.href; };
    }
}

/**
 * Set polyfill — ES6 Set
 */
if (typeof Set === 'undefined') {
    function Set(iterable) {
        this._items = [];
        var self = this;
        if (iterable) {
            for (var i = 0; i < iterable.length; i++) self.add(iterable[i]);
        }
    }
    Set.prototype.add = function(value) {
        if (!this.has(value)) this._items.push(value);
        return this;
    };
    Set.prototype.has = function(value) {
        return this._items.indexOf(value) !== -1;
    };
    Set.prototype.delete = function(value) {
        var i = this._items.indexOf(value);
        if (i !== -1) { this._items.splice(i, 1); return true; }
        return false;
    };
    Set.prototype.clear = function() { this._items = []; };
    Set.prototype.forEach = function(cb) { this._items.forEach(cb); };
    Object.defineProperty(Set.prototype, 'size', { get: function() { return this._items.length; } });
}

/**
 * Map polyfill — ES6 Map
 */
if (typeof Map === 'undefined') {
    function Map(iterable) {
        this._keys = []; this._values = [];
        var self = this;
        if (iterable) {
            for (var i = 0; i < iterable.length; i++) self.set(iterable[i][0], iterable[i][1]);
        }
    }
    Map.prototype.set = function(key, value) {
        var i = this._keys.indexOf(key);
        if (i !== -1) this._values[i] = value;
        else { this._keys.push(key); this._values.push(value); }
        return this;
    };
    Map.prototype.get = function(key) {
        var i = this._keys.indexOf(key);
        return i !== -1 ? this._values[i] : undefined;
    };
    Map.prototype.has = function(key) { return this._keys.indexOf(key) !== -1; };
    Map.prototype.delete = function(key) {
        var i = this._keys.indexOf(key);
        if (i !== -1) { this._keys.splice(i,1); this._values.splice(i,1); return true; }
        return false;
    };
    Map.prototype.clear = function() { this._keys=[]; this._values=[]; };
    Map.prototype.forEach = function(cb) {
        for (var i = 0; i < this._keys.length; i++) cb(this._values[i], this._keys[i], this);
    };
    Map.prototype.keys = function() { return this._keys.slice(); };
    Map.prototype.values = function() { return this._values.slice(); };
    Map.prototype.entries = function() {
        var result = [];
        for (var i = 0; i < this._keys.length; i++) result.push([this._keys[i], this._values[i]]);
        return result;
    };
    Object.defineProperty(Map.prototype, 'size', { get: function() { return this._keys.length; } });
}

/**
 * Array.from polyfill
 */
if (!Array.from) {
    Array.from = function(arrayLike, mapFn) {
        var arr = [];
        for (var i = 0; i < (arrayLike.length || 0); i++) {
            arr.push(mapFn ? mapFn(arrayLike[i], i) : arrayLike[i]);
        }
        return arr;
    };
}

/**
 * Object.entries / Object.values / Object.assign polyfills
 */
if (!Object.entries) {
    Object.entries = function(obj) {
        return Object.keys(obj).map(function(k) { return [k, obj[k]]; });
    };
}
if (!Object.values) {
    Object.values = function(obj) {
        return Object.keys(obj).map(function(k) { return obj[k]; });
    };
}
if (!Object.assign) {
    Object.assign = function(target) {
        for (var i = 1; i < arguments.length; i++) {
            var src = arguments[i];
            if (src) Object.keys(src).forEach(function(k) { target[k] = src[k]; });
        }
        return target;
    };
}

/**
 * String.prototype methods
 */
if (!String.prototype.startsWith) {
    String.prototype.startsWith = function(s, pos) {
        return this.indexOf(s, pos || 0) === (pos || 0);
    };
}
if (!String.prototype.endsWith) {
    String.prototype.endsWith = function(s) {
        return this.slice(-s.length) === s;
    };
}
if (!String.prototype.includes) {
    String.prototype.includes = function(s) { return this.indexOf(s) !== -1; };
}
if (!String.prototype.repeat) {
    String.prototype.repeat = function(n) {
        return new Array(Math.floor(n) + 1).join(this);
    };
}
if (!String.prototype.padStart) {
    String.prototype.padStart = function(len, ch) {
        ch = ch || ' ';
        var s = String(this);
        while (s.length < len) s = ch + s;
        return s;
    };
}
if (!String.prototype.padEnd) {
    String.prototype.padEnd = function(len, ch) {
        ch = ch || ' ';
        var s = String(this);
        while (s.length < len) s = s + ch;
        return s;
    };
}
if (!String.prototype.trimStart) {
    String.prototype.trimStart = function() { return this.replace(/^\s+/, ''); };
}
if (!String.prototype.trimEnd) {
    String.prototype.trimEnd = function() { return this.replace(/\s+$/, ''); };
}

/**
 * Array.prototype methods
 */
if (!Array.prototype.includes) {
    Array.prototype.includes = function(v) { return this.indexOf(v) !== -1; };
}
if (!Array.prototype.find) {
    Array.prototype.find = function(cb) {
        for (var i = 0; i < this.length; i++) if (cb(this[i], i, this)) return this[i];
        return undefined;
    };
}
if (!Array.prototype.findIndex) {
    Array.prototype.findIndex = function(cb) {
        for (var i = 0; i < this.length; i++) if (cb(this[i], i, this)) return i;
        return -1;
    };
}
if (!Array.prototype.flat) {
    Array.prototype.flat = function(depth) {
        depth = depth === undefined ? 1 : depth;
        var result = [];
        (function flatten(arr, d) {
            arr.forEach(function(item) {
                if (Array.isArray(item) && d > 0) flatten(item, d - 1);
                else result.push(item);
            });
        })(this, depth);
        return result;
    };
}
if (!Array.prototype.flatMap) {
    Array.prototype.flatMap = function(cb) {
        return this.map(cb).flat(1);
    };
}

/**
 * Number utilities
 */
if (!Number.isInteger) {
    Number.isInteger = function(v) { return typeof v === 'number' && isFinite(v) && Math.floor(v) === v; };
}
if (!Number.isFinite) {
    Number.isFinite = function(v) { return typeof v === 'number' && isFinite(v); };
}
if (!Number.isNaN) {
    Number.isNaN = function(v) { return typeof v === 'number' && isNaN(v); };
}
if (!Number.parseInt) { Number.parseInt = parseInt; }
if (!Number.parseFloat) { Number.parseFloat = parseFloat; }

/**
 * Promise polyfill (minimal sync version cho Rhino)
 * Rhino không có event loop nên Promise phải chạy sync.
 */
if (typeof Promise === 'undefined') {
    function Promise(executor) {
        this._value = undefined;
        this._error = undefined;
        this._resolved = false;
        this._rejected = false;
        var self = this;
        var resolve = function(v) { self._value = v; self._resolved = true; };
        var reject = function(e) { self._error = e; self._rejected = true; };
        try { executor(resolve, reject); } catch(e) { reject(e); }
    }
    Promise.prototype.then = function(onFulfilled, onRejected) {
        if (this._resolved && onFulfilled) {
            try { return Promise.resolve(onFulfilled(this._value)); }
            catch(e) { return Promise.reject(e); }
        }
        if (this._rejected && onRejected) {
            try { return Promise.resolve(onRejected(this._error)); }
            catch(e) { return Promise.reject(e); }
        }
        return this;
    };
    Promise.prototype.catch = function(onRejected) { return this.then(null, onRejected); };
    Promise.prototype.finally = function(cb) {
        return this.then(function(v) { cb(); return v; }, function(e) { cb(); throw e; });
    };
    Promise.resolve = function(v) {
        return new Promise(function(res) { res(v); });
    };
    Promise.reject = function(e) {
        return new Promise(function(_, rej) { rej(e); });
    };
    Promise.all = function(promises) {
        var results = [];
        for (var i = 0; i < promises.length; i++) {
            if (promises[i] && typeof promises[i].then === 'function') {
                if (promises[i]._rejected) return Promise.reject(promises[i]._error);
                results.push(promises[i]._value);
            } else {
                results.push(promises[i]);
            }
        }
        return Promise.resolve(results);
    };
}



/**
 * $.QA(element, selector) — query all elements, trả về HtmlElements
 * $.Q(element, selector)  — query first element, trả về HtmlElement
 */
var $ = {
    QA: function(element, selector) {
        if (!element || !selector) return { length: 0, size: function(){return 0;}, forEach: function(){}, map: function(){return[];}, get: function(){return null;}, first: function(){return null;} };
        return element.select(selector);
    },
    Q: function(element, selector) {
        if (!element || !selector) return { text: function(){return '';}, attr: function(){return '';}, html: function(){return '';}, select: function(){return $._empty();} };
        var els = element.select(selector);
        if (!els || els.length === 0) return { text: function(){return '';}, attr: function(){return '';}, html: function(){return '';}, select: function(){return $._empty();} };
        return els.first();
    },
    _empty: function() {
        return { length: 0, size: function(){return 0;}, forEach: function(){}, map: function(){return[];}, first: function(){return null;} };
    }
};

/**
 * String.format(template, ...args) — Java-style string format
 * Hỗ trợ: %s, %d, %f, {0} {1}...
 */
if (!String.format) {
    String.format = function(template) {
        var args = Array.prototype.slice.call(arguments, 1);
        return template
            .replace(/\{(\d+)\}/g, function(m, i) { return args[i] !== undefined ? args[i] : m; })
            .replace(/%s/g, function() { return args.shift !== undefined ? String(args.shift() || '') : '%s'; })
            .replace(/%d/g, function() { return args.shift !== undefined ? parseInt(args.shift() || 0) : '%d'; });
    };
}

/**
 * log(msg) — standalone log function (shortcut cho Console.log)
 */
function log(msg) {
    return __(['Log_log', String(msg)]);
}

