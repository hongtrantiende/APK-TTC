load('config.js');

var LEN_EXTRA = [0,0,0,0,0,0,0,0,1,1,1,1,2,2,2,2,3,3,3,3,4,4,4,4,5,5,5,5,0];
var LEN_BASE = [3,4,5,6,7,8,9,10,11,13,15,17,19,23,27,31,35,43,51,59,67,83,99,115,131,163,195,227,258];
var DIST_EXTRA = [0,0,0,0,1,1,2,2,3,3,4,4,5,5,6,6,7,7,8,8,9,9,10,10,11,11,12,12,13,13];
var DIST_BASE = [1,2,3,4,5,7,9,13,17,25,33,49,65,97,129,193,257,385,513,769,1025,1537,2049,3073,4097,6145,8193,12289,16385,24577];

function inflate(data) {
    var state = { bitPos: 0, data: data };
    var out = [];

    function readBits(n) {
        var val = 0;
        for (var j = 0; j < n; j++) {
            var byteIdx = state.bitPos >> 3;
            var bitIdx = state.bitPos & 7;
            val |= ((state.data[byteIdx] >> bitIdx) & 1) << j;
            state.bitPos++;
        }
        return val;
    }

    function decodeSym(tree) {
        var code = 0;
        var len = 0;
        while (len <= tree.maxLen) {
            var byteIdx = state.bitPos >> 3;
            var bitIdx = state.bitPos & 7;
            code = (code << 1) | ((state.data[byteIdx] >> bitIdx) & 1);
            state.bitPos++;
            len++;
            for (var i = 0; i < tree.codes.length; i++) {
                if (tree.codes[i].len === len && tree.codes[i].code === code) return tree.codes[i].sym;
            }
        }
        return -1;
    }

    function buildTree(lengths) {
        var maxLen = 0;
        for (var i = 0; i < lengths.length; i++) if (lengths[i] > maxLen) maxLen = lengths[i];
        var blCount = [];
        for (var i = 0; i <= maxLen; i++) blCount.push(0);
        for (var i = 0; i < lengths.length; i++) blCount[lengths[i]]++;
        var nextCode = [];
        var code = 0;
        blCount[0] = 0;
        for (var bits = 1; bits <= maxLen; bits++) {
            code = (code + blCount[bits - 1]) << 1;
            nextCode[bits] = code;
        }
        var codes = [];
        for (var n = 0; n < lengths.length; n++) {
            var len = lengths[n];
            if (len > 0) codes.push({ sym: n, len: len, code: nextCode[len]++ });
        }
        return { codes: codes, maxLen: maxLen };
    }

    var bfinal = 0;
    while (!bfinal) {
        bfinal = readBits(1);
        var btype = readBits(2);
        if (btype === 0) {
            state.bitPos = (state.bitPos + 7) & ~7;
            var bytePos = state.bitPos >> 3;
            var len = state.data[bytePos] | (state.data[bytePos + 1] << 8);
            bytePos += 4;
            state.bitPos += 32 + len * 8;
            for (var k = 0; k < len; k++) out.push(state.data[bytePos + k]);
        } else if (btype === 1 || btype === 2) {
            var litLens = [];
            var distLens = [];
            if (btype === 1) {
                for (var k = 0; k < 288; k++) litLens.push(k < 144 ? 8 : (k < 256 ? 9 : (k < 280 ? 7 : 8)));
                for (var k = 0; k < 32; k++) distLens.push(5);
            } else {
                var hlit = readBits(5) + 257;
                var hdist = readBits(5) + 1;
                var hclen = readBits(4) + 4;
                var CL_ORDER = [16,17,18,0,8,7,9,6,10,5,11,4,12,3,13,2,14,1,15];
                var clLens = [];
                for (var k = 0; k < 19; k++) clLens.push(0);
                for (var k = 0; k < hclen; k++) clLens[CL_ORDER[k]] = readBits(3);
                var clTree = buildTree(clLens);
                var allLens = [];
                while (allLens.length < hlit + hdist) {
                    var sym = decodeSym(clTree);
                    if (sym < 16) allLens.push(sym);
                    else if (sym === 16) {
                        var rep = readBits(2) + 3;
                        var last = allLens[allLens.length - 1];
                        for (var k = 0; k < rep; k++) allLens.push(last);
                    } else if (sym === 17) {
                        var rep = readBits(3) + 3;
                        for (var k = 0; k < rep; k++) allLens.push(0);
                    } else if (sym === 18) {
                        var rep = readBits(7) + 11;
                        for (var k = 0; k < rep; k++) allLens.push(0);
                    }
                }
                litLens = allLens.slice(0, hlit);
                distLens = allLens.slice(hlit, hlit + hdist);
            }
            var litTree = buildTree(litLens);
            var distTree = buildTree(distLens);
            while (true) {
                var sym = decodeSym(litTree);
                if (sym === 256) break;
                if (sym < 256) out.push(sym);
                else {
                    var li = sym - 257;
                    var length = LEN_BASE[li] + (LEN_EXTRA[li] > 0 ? readBits(LEN_EXTRA[li]) : 0);
                    var ds = decodeSym(distTree);
                    var dist = DIST_BASE[ds] + (DIST_EXTRA[ds] > 0 ? readBits(DIST_EXTRA[ds]) : 0);
                    for (var k = 0; k < length; k++) out.push(out[out.length - dist]);
                }
            }
        } else {
            break;
        }
    }
    return out;
}

function bytesToUtf8(bytes) {
    var str = "";
    var i = 0;
    while (i < bytes.length) {
        var c = bytes[i++] & 255;
        if (c < 128) str += String.fromCharCode(c);
        else if (c < 224) str += String.fromCharCode(((c & 31) << 6) | (bytes[i++] & 63));
        else if (c < 240) str += String.fromCharCode(((c & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63));
        else {
            var cp = ((c & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63);
            cp -= 65536;
            str += String.fromCharCode(55296 + (cp >> 10), 56320 + (cp & 1023));
        }
    }
    return str;
}

function normalizeBytes(byteArray) {
    var bytes = [];
    if (byteArray) {
        for (var i = 0; i < byteArray.length; i++) bytes.push(byteArray[i] & 255);
    }
    return bytes;
}

function fetchVipContent(slug, chapter) {
    if (!AUTH_TOKEN) return { debug: "NO_AUTH_TOKEN" };
    try {
        let apiUrl = API_URL + "/chapters/" + encodeURIComponent(slug) + "/content/" + chapter + "?platform=web";
        let response = authFetch(apiUrl);
        if (!response.ok) return { debug: "VIP_API_FAIL:" + response.status };

        let json = response.json();
        let content = json && json.content ? json.content : null;
        if (!content) return { debug: "VIP_API_NO_CONTENT" };

        response = fetch(content);
        if (!response.ok) return { debug: "S3_FETCH_FAIL:" + response.status };
        
        // vBook response object might not have bytes() or it might crash
        let text = "";
        
        // Strategy A: Try text() directly (simplest)
        try {
            let t = response.text();
            if (isReadableText(t)) return responseContent(t);
            text = t;
        } catch (e1) {}

        // Strategy B: Try manual ungzip on bytes
        try {
            let b = response.bytes();
            if (b) {
                let decoded = ungzipBytes(b);
                if (isReadableText(decoded)) return responseContent(decoded);
            }
        } catch (e2) {}

        // Strategy C: Try ungzip on text
        try {
            let decoded = ungzipText(text);
            if (isReadableText(decoded)) return responseContent(decoded);
        } catch (e3) {}

        return { debug: "ALL_DECODE_STRATEGIES_FAILED", sample: (text || "").substring(0, 50) };
    } catch (globalError) {
        return { debug: "GLOBAL_FETCH_ERROR:" + globalError.message };
    }
}
    }
    return str;
}

function normalizeBytes(byteArray) {
    var bytes = [];
    for (var i = 0; i < byteArray.length; i++) bytes.push(byteArray[i] & 255);
    return bytes;
}

function textToBytes(text) {
    var bytes = [];
    for (var i = 0; i < text.length; i++) bytes.push(text.charCodeAt(i) & 255);
    return bytes;
}

function ungzipBytes(byteArray) {
    byteArray = normalizeBytes(byteArray);
    if (!byteArray || byteArray.length < 10 || byteArray[0] !== 31 || byteArray[1] !== 139) {
        return bytesToUtf8(byteArray);
    }
    var pos = 10;
    var flags = byteArray[3];
    if (flags & 4) {
        var xlen = byteArray[pos] | (byteArray[pos + 1] << 8);
        pos += 2 + xlen;
    }
    if (flags & 8) while (pos < byteArray.length && byteArray[pos++] !== 0) {}
    if (flags & 16) while (pos < byteArray.length && byteArray[pos++] !== 0) {}
    if (flags & 2) pos += 2;
    var deflateData = [];
    for (var i = pos; i < byteArray.length - 8; i++) deflateData.push(byteArray[i]);
    return bytesToUtf8(inflate(deflateData));
}

function ungzipText(raw) {
    var bytes = textToBytes(raw);
    if (bytes.length < 10 || bytes[0] !== 31 || bytes[1] !== 139) return raw;
    var pos = 10;
    var flags = bytes[3];
    if (flags & 4) {
        var xlen = bytes[pos] | (bytes[pos + 1] << 8);
        pos += 2 + xlen;
    }
    if (flags & 8) while (pos < bytes.length && bytes[pos++] !== 0) {}
    if (flags & 16) while (pos < bytes.length && bytes[pos++] !== 0) {}
    if (flags & 2) pos += 2;
    var deflateData = bytes.slice(pos, bytes.length - 8);
    return bytesToUtf8(inflate(deflateData));
}

function isReadableText(text) {
    if (!text || !cleanText(text)) return false;
    if (text.indexOf("\u001f") === 0 || text.charCodeAt(0) === 31) return false;
    var sample = text.substring(0, 200);
    var bad = 0;
    for (var i = 0; i < sample.length; i++) {
        var c = sample.charCodeAt(i);
        if ((c < 32 && c !== 10 && c !== 13 && c !== 9) || c === 65533) bad++;
    }
    return bad < 5;
}

function lockedMessage() {
    let lsStatus = "unknown";
    let lsValue = "";
    let lsRaw = "";
    try {
        if (typeof localStorage !== "undefined") {
            lsStatus = "available";
            try {
                lsValue = localStorage.getItem("auth-storage") || "";
                lsRaw = lsValue.substring(0, 50);
            } catch (e) {}
        } else {
            lsStatus = "unavailable";
        }
    } catch (e) {
        lsStatus = "error:" + e.message;
    }
    
    let configStatus = "unknown";
    try {
        if (CONFIG_URL) {
            configStatus = "set:" + CONFIG_URL.substring(0, 30) + "...";
        } else {
            configStatus = "unset";
        }
    } catch (e) {
        configStatus = "error";
    }
    
    let tokenStatus = AUTH_TOKEN ? ("present:" + AUTH_TOKEN.substring(0, 20) + "...") : "missing";
    let cookieStatus = AUTH_COOKIE ? ("present:" + AUTH_COOKIE.substring(0, 20) + "...") : "missing";
    
    let extracted = "";
    try {
        if (lsValue) extracted = extractAuthToken(lsValue);
    } catch (e) {}
    
    return "<p><i>Chương này bị khóa/VIP. Debug: TOKEN=" + tokenStatus + " | COOKIE=" + cookieStatus + " | LS=" + lsStatus + " | LS_RAW=" + lsRaw + " | EXTRACTED=" + (extracted ? extracted.substring(0, 20) : "empty") + " | CONFIG=" + configStatus + "</i></p>";
}

function fetchVipContent(slug, chapter) {
    if (!AUTH_TOKEN) return { debug: "NO_AUTH_TOKEN" };
    let apiUrl = API_URL + "/chapters/" + encodeURIComponent(slug) + "/content/" + chapter + "?platform=web";
    let response = authFetch(apiUrl);
    if (!response.ok) return { debug: "VIP_API_FAIL:" + response.status };

    let json = response.json();
    let content = json && json.content ? json.content : null;
    if (!content) return { debug: "VIP_API_NO_CONTENT", json: JSON.stringify(json) };

    response = fetch(content);
    if (!response.ok) return { debug: "S3_FETCH_FAIL:" + response.status };
    
    // Try to get raw bytes first (for gzip), fallback to text
    let text;
    try {
        let bytes = response.bytes();
        if (bytes && bytes.length > 0) {
            text = ungzipBytes(bytes);
        }
    } catch (e) {
        text = null;
    }
    
    if (!text) {
        let rawText = response.text();
        text = ungzipText(rawText);
    }
    
    if (!text || !isReadableText(text)) return { debug: "UNGZIP_FAIL", sample: text ? text.substring(0, 100) : "null" };
    return responseContent(text);
}

function extractChapterHtml(doc) {
    let selectors = [
        ".content .space-y-3",
        "div.space-y-3",
        "div[class*='space-y-3']",
        ".content",
        ".chapter-content",
        "#chapter-content",
        "#content",
        ".reading-content"
    ];

    for (let i = 0; i < selectors.length; i++) {
        let content = doc.select(selectors[i]).first();
        if (content) {
            content.select("script, button, svg, ins, iframe").remove();
            let html = content.html();
            if (cleanText(stripHtml(html))) return html;
        }
    }

    let paragraphs = doc.select("p.leading-relaxed");
    if (paragraphs.size() > 0) {
        let html = "";
        for (let i = 0; i < paragraphs.size(); i++) html += paragraphs.get(i).outerHtml();
        if (cleanText(stripHtml(html))) return html;
    }

    return null;
}

function execute(url) {
    url = normalizeUrl(url);
    let slug = getSlug(url);
    let chapter = getChapterNumber(url);
    let book = fetchJson(API_URL + "/books/" + encodeURIComponent(slug));
    let lockChapters = book && book.lockChapters ? parseInt(book.lockChapters) : 50;
    let isFreeBook = book && book.isFree;

    // Read auth token fresh from localStorage (may not be available at config.js load time)
    try {
        if (!AUTH_TOKEN && typeof localStorage !== "undefined") {
            let authStorage = localStorage.getItem("auth-storage");
            if (authStorage) {
                let token = extractAuthToken(authStorage);
                if (token) AUTH_TOKEN = token;
            }
        }
    } catch (e) {}

    let freeUrl = DATA_URL + "/free/" + slug + "/chuong-" + chapter + ".txt";
    let response = fetch(freeUrl);
    if (response.ok) {
        let text = ungzipText(response.text());
        let html = isReadableText(text) ? responseContent(text) : null;
        if (html) return Response.success(html);
    }

    let vipResult = fetchVipContent(slug, chapter);
    if (typeof vipResult === "string" && vipResult) return Response.success(vipResult);
    if (vipResult && vipResult.debug) {
        return Response.success("<p><i>VIP Debug: " + vipResult.debug + (vipResult.json ? " | JSON:" + vipResult.json.substring(0, 100) : "") + (vipResult.sample ? " | SAMPLE:" + vipResult.sample : "") + "</i></p>" + lockedMessage());
    }

    response = authFetch(url);
    if (response.ok) {
        let doc = response.html();
        let html = extractChapterHtml(doc);
        if (html && (isFreeBook || chapter <= lockChapters || html.indexOf("Mở khóa") === -1)) {
            if (html.indexOf("Chương này bị khóa") === -1 && html.indexOf("VIP") === -1 && html.indexOf("chỉ dành cho thành viên") === -1) {
                return Response.success(html);
            }
        }
    }

    response = fetch(url);
    if (response.ok) {
        let html = response.text();
        if (html && html.indexOf("Mở khóa") === -1 && html.indexOf("chỉ dành cho thành viên") === -1) {
            let doc = response.html();
            let content = extractChapterHtml(doc);
            if (content) return Response.success(content);
        }
    }

    return Response.success(lockedMessage());
}
