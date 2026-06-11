load("config.js");

// Fallback sources based on the website structure - all available sources
const FALLBACK_SOURCES = [
    'fanqie', 'drxsw', 'qidian', 'uukanshu', '69shuba', 'czbooks', 
    '52shuku', 'biqu5200', '8book', 'banxiacc', 'shuhaige', 'greenbon', 
    'jjwxc', '51read', 'linovelb', 'piaotia', 'trxssc', 'trxsme', 
    'jpxs123', 'longrenguan', 'qbtf', 'qimao', '69yd', 'faloo', 
    'zuzaxiaoshuo'
];

function abs(u) {
    if (!u) return "";
    if (u.startsWith("http")) return u;
    if (u.startsWith("//")) return "https:" + u;
    return BASE_URL + (u.startsWith("/") ? "" : "/") + u;
}

function extractSlug(u) {
    // Extract slug from URLs like /truyen/tu-bao-tien-bon
    let m = u.match(/\/truyen\/([^/?#]+)/i);
    return m ? m[1] : "";
}

function safeJson(res) {
    try {
        if (!res) return null;
        if (typeof res.json === 'function') {
            return res.json();
        }
    } catch (e) {}
    try {
        const text = res && typeof res.text === 'function' ? res.text() : "";
        if (text && text.trim().length) return JSON.parse(text);
    } catch (e) {}
    return null;
}

function parseChapterFromHtml(doc) {
    let chapters = [];
    
    // Parse from the chapter list in "DS chương" tab - source-tab-content sections
    doc.select("#ds-chuong .source-tab-content ul li a").forEach(a => {
        let name = (a.text() || "").trim();
        let href = a.attr("href") || "";
        if (!name || !href) return;
        chapters.push({ 
            name: name, 
            url: abs(href), 
            host: BASE_URL 
        });
    });
    
    return chapters;
}

function parseChapterFromApi(res, slug, source) {
    const json = safeJson(res);
    let chapters = [];
    
    if (json && json.data && Array.isArray(json.data)) {
        json.data.forEach(item => {
            const title = (item.title || item.name || "").trim();
            const chapterSlug = item.chapter_url || item.url || item.slug || "";
            if (!title || !chapterSlug) return;
            
            // Build chapter URL: /truyen/slug/source/chapter
            const chapterUrl = BASE_URL + "/truyen/" + slug + "/" + source + "/" + chapterSlug;
            chapters.push({
                name: title,
                url: chapterUrl,
                host: BASE_URL
            });
        });
    }
    
    return chapters;
}

function getAvailableSources(doc) {
    const sources = [];
    
    // Find source tabs with data-source attribute in the "DS chương" tab section
    doc.select("#ds-chuong #source-tabs .source-tab-btn[data-source]").forEach(btn => {
        const source = btn.attr("data-source");
        if (source && source.trim()) {
            sources.push(source.trim());
        }
    });
    
    return sources.length > 0 ? sources : FALLBACK_SOURCES;
}

function sortChapters(chapters) {
    // Sort by chapter number if present
    const getChapterNumber = (name) => {
        const match = name.match(/(?:Chương|Chapter|第)\s*(\d+)/i);
        return match ? parseInt(match[1]) : 0;
    };
    
    return chapters.sort((a, b) => {
        const numA = getChapterNumber(a.name);
        const numB = getChapterNumber(b.name);
        return numA - numB;
    });
}

function removeDuplicates(chapters) {
    const seen = new Set();
    return chapters.filter(chapter => {
        const key = chapter.url;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
}

function execute(url) {
    let res = fetch(url);
    if (!res || !res.ok) return Response.success([]);
    
    let doc = res.html();
    const slug = extractSlug(url);
    if (!slug) return Response.success([]);
    
    // Get available sources from the "DS chương" tab
    const sources = getAvailableSources(doc);
    let allChapters = [];
    
    // Try each source to get chapter list
    for (let source of sources) {
        try {
            // Try different possible endpoints
            const endpoints = [
                BASE_URL + "/truyen/" + slug + "/tab_content/" + source,
                BASE_URL + "/api/truyen/" + slug + "/" + source + "/chapters",
                BASE_URL + "/truyen/" + slug + "/" + source + "/chapters"
            ];
            
            for (let endpoint of endpoints) {
                let sourceRes = fetch(endpoint);
                if (!sourceRes || !sourceRes.ok) continue;
                
                // Try parsing as JSON first
                const apiChapters = parseChapterFromApi(sourceRes, slug, source);
                if (apiChapters.length > 0) {
                    if (sources.length > 1) {
                        apiChapters.forEach(ch => {
                            ch.name = ch.name + " (" + source + ")";
                        });
                    }
                    allChapters = allChapters.concat(apiChapters);
                    break; // Found chapters for this source, move to next source
                }
                
                // Try parsing as HTML if JSON failed
                const sourceDoc = sourceRes.html();
                if (sourceDoc) {
                    const htmlChapters = parseChapterFromHtml(sourceDoc);
                    if (htmlChapters.length > 0) {
                        if (sources.length > 1) {
                            htmlChapters.forEach(ch => {
                                ch.name = ch.name + " (" + source + ")";
                            });
                        }
                        allChapters = allChapters.concat(htmlChapters);
                        break; // Found chapters for this source, move to next source
                    }
                }
            }
        } catch (e) {
            // Continue to next source if this one fails
            continue;
        }
    }
    
    // If no chapters found from any source, try parsing from main page
    if (allChapters.length === 0) {
        allChapters = parseChapterFromHtml(doc);
    }
    
    // Clean up and sort
    allChapters = removeDuplicates(allChapters);
    allChapters = sortChapters(allChapters);
    
    return Response.success(allChapters);
}