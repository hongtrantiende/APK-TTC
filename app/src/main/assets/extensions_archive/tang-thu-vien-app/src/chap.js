load("config.js");
function execute(url) {
    sleep(Math.random() * (2500- 1000) + 1000);
    const secret = "174587236491eyoruwoiernzwueyquhszsadhajsdha8";
    const token = "";
    const userId = "0";
console.log("dd");
    if (!url) return null;

    let parts = String(url).split("__");
    if (parts.length < 2) return null;

    let storyId = parts[1];
    let chapterId = parts[2];
    if (!storyId || !chapterId) return null;

    let payload = {
        id_chapter: chapterId,
        id_story: storyId,
        user_id: userId,
        hash: sha256(token + chapterId + storyId + userId + secret)
    };

    let response = fetch(API_BASE + "/ttv/ttv_apiv2/public/get_content_chapter", {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "token": token
        },
        body: "get_content_chapter=" + encodeURIComponent(JSON.stringify(payload))
    });

    if (!response.ok) return null;

    let data = response.json();
    let contentList = data && data.content_chapter ? data.content_chapter : (data ? data.content : null);
    if (!contentList || !contentList.length || !contentList[0].content) return null;

    let html = contentList[0].content.replace(/\r?\n/g, "<br>");
    return Response.success(html);
}

function sha256(ascii) {
    function rightRotate(value, amount) {
        return (value >>> amount) | (value << (32 - amount));
    }

    let mathPow = Math.pow;
    let maxWord = mathPow(2, 32);
    let lengthProperty = "length";
    let i, j;
    let result = "";
    let words = [];
    let asciiBitLength = ascii[lengthProperty] * 8;

    let hash = sha256.h = sha256.h || [];
    let k = sha256.k = sha256.k || [];
    let primeCounter = k[lengthProperty];

    let isComposite = {};
    for (let candidate = 2; primeCounter < 64; candidate++) {
        if (!isComposite[candidate]) {
            for (i = 0; i < 313; i += candidate) {
                isComposite[i] = candidate;
            }
            hash[primeCounter] = (mathPow(candidate, 0.5) * maxWord) | 0;
            k[primeCounter++] = (mathPow(candidate, 1 / 3) * maxWord) | 0;
        }
    }

    ascii += "\x80";
    while (ascii[lengthProperty] % 64 - 56) ascii += "\x00";
    for (i = 0; i < ascii[lengthProperty]; i++) {
        j = ascii.charCodeAt(i);
        words[i >> 2] |= j << ((3 - i) % 4) * 8;
    }
    words[words[lengthProperty]] = (asciiBitLength / maxWord) | 0;
    words[words[lengthProperty]] = asciiBitLength;

    for (j = 0; j < words[lengthProperty];) {
        let w = words.slice(j, (j += 16));
        let oldHash = hash.slice(0);

        for (i = 0; i < 64; i++) {
            let w15 = w[i - 15];
            let w2 = w[i - 2];

            let a = hash[0];
            let e = hash[4];
            let temp1 =
                hash[7] +
                (rightRotate(e, 6) ^ rightRotate(e, 11) ^ rightRotate(e, 25)) +
                ((e & hash[5]) ^ (~e & hash[6])) +
                k[i] +
                (w[i] = i < 16
                    ? w[i]
                    : (w[i - 16] +
                        (rightRotate(w15, 7) ^ rightRotate(w15, 18) ^ (w15 >>> 3)) +
                        w[i - 7] +
                        (rightRotate(w2, 17) ^ rightRotate(w2, 19) ^ (w2 >>> 10))) | 0);
            let temp2 =
                (rightRotate(a, 2) ^ rightRotate(a, 13) ^ rightRotate(a, 22)) +
                ((a & hash[1]) ^ (a & hash[2]) ^ (hash[1] & hash[2]));

            hash = [(temp1 + temp2) | 0].concat(hash);
            hash[4] = (hash[4] + temp1) | 0;
            hash.pop();
        }

        for (i = 0; i < 8; i++) {
            hash[i] = (hash[i] + oldHash[i]) | 0;
        }
    }

    for (i = 0; i < 8; i++) {
        for (j = 3; j + 1; j--) {
            let b = (hash[i] >> (j * 8)) & 255;
            result += (b < 16 ? "0" : "") + b.toString(16);
        }
    }
    return result;
}
