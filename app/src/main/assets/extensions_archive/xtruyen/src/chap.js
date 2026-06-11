load('config.js');

var BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
var URL64 = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ-_";

function url64ToBase64(str) {
    var out = "";
    for (var i = 0; i < str.length; i++) {
        var ch = str.charAt(i);
        var idx = URL64.indexOf(ch);
        out += idx > -1 ? BASE64.charAt(idx) : ch;
    }
    return out;
}

function b64ToBytes(b64) {
    b64 = b64.replace(/[^A-Za-z0-9+\/=]/g, "");
    var bytes = [];
    var i, b10, imax = b64.length;
    var pads = 0;
    if (imax > 0) {
        if (b64.charAt(imax - 1) === "=") {
            pads = 1;
            if (imax > 1 && b64.charAt(imax - 2) === "=") pads = 2;
            imax -= 4;
        }
    }
    for (i = 0; i < imax; i += 4) {
        b10 = (BASE64.indexOf(b64.charAt(i)) << 18) |
              (BASE64.indexOf(b64.charAt(i + 1)) << 12) |
              (BASE64.indexOf(b64.charAt(i + 2)) << 6) |
              BASE64.indexOf(b64.charAt(i + 3));
        bytes.push((b10 >> 16) & 255, (b10 >> 8) & 255, b10 & 255);
    }
    if (pads === 1) {
        b10 = (BASE64.indexOf(b64.charAt(i)) << 18) |
              (BASE64.indexOf(b64.charAt(i + 1)) << 12) |
              (BASE64.indexOf(b64.charAt(i + 2)) << 6);
        bytes.push((b10 >> 16) & 255, (b10 >> 8) & 255);
    } else if (pads === 2) {
        b10 = (BASE64.indexOf(b64.charAt(i)) << 18) |
              (BASE64.indexOf(b64.charAt(i + 1)) << 12);
        bytes.push((b10 >> 16) & 255);
    }
    return bytes;
}

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
                if (tree.codes[i].len === len && tree.codes[i].code === code) {
                    return tree.codes[i].sym;
                }
            }
        }
        return -1;
    }

    function buildTree(lengths) {
        var maxLen = 0;
        for (var i = 0; i < lengths.length; i++) {
            if (lengths[i] > maxLen) maxLen = lengths[i];
        }
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
            if (len > 0) {
                codes.push({sym: n, len: len, code: nextCode[len]++});
            }
        }
        return {codes: codes, maxLen: maxLen};
    }

    var bfinal = 0;
    while (!bfinal) {
        bfinal = readBits(1);
        var btype = readBits(2);

        if (btype === 0) {
            state.bitPos = (state.bitPos + 7) & ~7;
            var bytePos = state.bitPos >> 3;
            var len = state.data[bytePos] | (state.data[bytePos + 1] << 8);
            var nlen = state.data[bytePos + 2] | (state.data[bytePos + 3] << 8);
            bytePos += 4;
            state.bitPos += 32 + len * 8;
            for (var k = 0; k < len; k++) {
                out.push(state.data[bytePos + k]);
            }
        } else if (btype === 1) {
            var litLens = [];
            for (var k = 0; k < 288; k++) litLens.push(k < 144 ? 8 : (k < 256 ? 9 : (k < 280 ? 7 : 8)));
            var distLens = [];
            for (var k = 0; k < 32; k++) distLens.push(5);
            var litTree = buildTree(litLens);
            var distTree = buildTree(distLens);

            while (true) {
                var sym = decodeSym(litTree);
                if (sym === 256) break;
                if (sym < 256) {
                    out.push(sym);
                } else {
                    var li = sym - 257;
                    var length = LEN_BASE[li] + (LEN_EXTRA[li] > 0 ? readBits(LEN_EXTRA[li]) : 0);
                    var ds = decodeSym(distTree);
                    var dist = DIST_BASE[ds] + (DIST_EXTRA[ds] > 0 ? readBits(DIST_EXTRA[ds]) : 0);
                    for (var k = 0; k < length; k++) {
                        out.push(out[out.length - dist]);
                    }
                }
            }
        } else if (btype === 2) {
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
                if (sym < 16) {
                    allLens.push(sym);
                } else if (sym === 16) {
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

            var litLens = allLens.slice(0, hlit);
            var distLens2 = allLens.slice(hlit, hlit + hdist);
            var litTree = buildTree(litLens);
            var distTree = buildTree(distLens2);

            while (true) {
                var sym = decodeSym(litTree);
                if (sym === 256) break;
                if (sym < 256) {
                    out.push(sym);
                } else {
                    var li = sym - 257;
                    var length = LEN_BASE[li] + (LEN_EXTRA[li] > 0 ? readBits(LEN_EXTRA[li]) : 0);
                    var ds = decodeSym(distTree);
                    var dist = DIST_BASE[ds] + (DIST_EXTRA[ds] > 0 ? readBits(DIST_EXTRA[ds]) : 0);
                    for (var k = 0; k < length; k++) {
                        out.push(out[out.length - dist]);
                    }
                }
            }
        }
    }
    return out;
}

function bytesToUtf8(bytes) {
    var str = "";
    var i = 0;
    while (i < bytes.length) {
        var c = bytes[i++];
        if (c < 128) {
            str += String.fromCharCode(c);
        } else if (c < 224) {
            str += String.fromCharCode(((c & 31) << 6) | (bytes[i++] & 63));
        } else if (c < 240) {
            str += String.fromCharCode(((c & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63));
        } else {
            var cp = ((c & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63);
            cp -= 65536;
            str += String.fromCharCode(55296 + (cp >> 10), 56320 + (cp & 1023));
        }
    }
    return str;
}

function execute(url) {
    url = normalizeUrl(url);
    var response = fetch(url);
    if (!response.ok) return null;

    var html = response.html().html();

    var dataXMatch = html.match(/const\s+data_x\s*=\s*"([^"]+)"/);
    if (!dataXMatch) {
        var content = response.html().select("#chapter-reading-content").first();
        if (content) {
            content.select("script, iframe, ins, style, .aam-ad-container, .aam-mvOXQg, .carousel, .spinner, #loading-box").remove();
            content.select(".chapter-title, .chapter_control, .chapter_wrap, .line-control, .chap-info").remove();
            content.select("#download-book, .btn-dschuong, .back, .next").remove();
            return Response.success(content.html());
        }
        return null;
    }

    var dataX = dataXMatch[1];
    var b64 = url64ToBase64(dataX);
    var bytes = b64ToBytes(b64);
    var decompressed = inflate(bytes);
    var text = bytesToUtf8(decompressed);

    text = text.replace(/(&nbsp;)*(\s*<br\s*\/?>\s*){2,}/gi, "<br><br>");

    return Response.success(text);
}