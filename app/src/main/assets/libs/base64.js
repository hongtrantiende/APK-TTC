/**
 * base64.js — Built-in Base64 library (global fallback)
 * Được load khi extension gọi load('base64.js') mà không có file riêng.
 * Cung cấp: Base64.encode(), Base64.decode(), atob(), btoa()
 */

var Base64 = (function() {
    var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

    function encode(input) {
        var str = String(input);
        var output = '';
        for (var block, charCode, idx = 0, map = chars;
             str.charAt(idx | 0) || (map = '=', idx % 1);
             output += map.charAt(63 & block >> 8 - idx % 1 * 8)) {
            charCode = str.charCodeAt(idx += 3/4);
            if (charCode > 0xFF) throw new Error("'btoa' failed: char out of range");
            block = block << 8 | charCode;
        }
        return output;
    }

    function decode(input) {
        var str = String(input).replace(/[=]+$/, '');
        if (str.length % 4 === 1) throw new Error("'atob' failed: wrong length");
        var output = '';
        for (var bc = 0, bs, buffer, idx = 0;
             buffer = str.charAt(idx++);
             ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer,
             bc++ % 4) ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6)) : 0) {
            buffer = chars.indexOf(buffer);
        }
        return output;
    }

    function encodeUnicode(str) {
        return encode(encodeURIComponent(str).replace(/%([0-9A-F]{2})/g, function(match, p1) {
            return String.fromCharCode('0x' + p1);
        }));
    }

    function decodeUnicode(str) {
        return decodeURIComponent(decode(str).split('').map(function(c) {
            return '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2);
        }).join(''));
    }

    return {
        encode: encode,
        decode: decode,
        encodeUnicode: encodeUnicode,
        decodeUnicode: decodeUnicode,
        fromBase64: decode,
        toBase64: encode
    };
})();

// Polyfill atob/btoa nếu chưa có
if (typeof atob === 'undefined') {
    function atob(input) { return Base64.decode(input); }
}
if (typeof btoa === 'undefined') {
    function btoa(input) { return Base64.encode(input); }
}
