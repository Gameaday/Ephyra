/**
 * CLI tool to transpile legacy Tachiyomi/Mihon Kotlin extension sources
 * into sandboxed JavaScript scrapers compatible with Ephyra.
 *
 * Usage:
 *   node scripts/convert_legacy_extension.js path/to/Extension.kt
 */

const fs = require('fs');
const path = require('path');

function printUsage() {
    console.log("Ephyra Legacy Extension Converter");
    console.log("=================================");
    console.log("Usage:");
    console.log("  node scripts/convert_legacy_extension.js <path-to-kotlin-source-file>");
    console.log("");
    console.log("Example:");
    console.log("  node scripts/convert_legacy_extension.js src/en/mangapark/MangaPark.kt");
}

if (process.argv.length < 3) {
    printUsage();
    process.exit(1);
}

const ktFilePath = path.resolve(process.argv[2]);
if (!fs.existsSync(ktFilePath)) {
    console.error(`Error: File not found at ${ktFilePath}`);
    process.exit(1);
}

const ktSource = fs.readFileSync(ktFilePath, 'utf8');

// 1. Extract metadata
const nameMatch = ktSource.match(/val\s+name\s*=\s*"([^"]+)"/) || ktSource.match(/override\s+val\s+name\s*=\s*"([^"]+)"/);
const baseUrlMatch = ktSource.match(/val\s+baseUrl\s*=\s*"([^"]+)"/) || ktSource.match(/override\s+val\s+baseUrl\s*=\s*"([^"]+)"/);

const extensionName = nameMatch ? nameMatch[1] : path.basename(ktFilePath, '.kt');
const baseUrl = baseUrlMatch ? baseUrlMatch[1] : 'https://example.com';

console.log(`Converting extension: "${extensionName}"`);
console.log(`Base URL: "${baseUrl}"`);

// 2. Extract Selector strings
function extractSelector(name) {
    const reg = new RegExp(`override\\s+fun\\s+${name}\\s*\\(\\s*\\)\\s*(?::\\s*String\\s*)?=\\s*"([^"]+)"`);
    const match = ktSource.match(reg);
    if (match) return match[1];

    const regBlock = new RegExp(`override\\s+fun\\s+${name}\\s*\\(\\s*\\)\\s*(?::\\s*String\\s*)?\\{\\s*return\\s*"([^"]+)"\\s*\\}`);
    const matchBlock = ktSource.match(regBlock);
    return matchBlock ? matchBlock[1] : '';
}

const popularSelector = extractSelector('popularMangaSelector') || 'div.item';
const popularNextPage = extractSelector('popularMangaNextPageSelector') || 'a.next-page';
const searchSelector = extractSelector('searchMangaSelector') || popularSelector || 'div.item';
const searchNextPage = extractSelector('searchMangaNextPageSelector') || popularNextPage || 'a.next-page';
const chapterSelector = extractSelector('chapterListSelector') || 'div.chapter';

// Helper to handle balanced parentheses for setUrlWithoutDomain
function replaceSetUrlWithoutDomain(body) {
    let index = 0;
    while ((index = body.indexOf(".setUrlWithoutDomain(", index)) !== -1) {
        let bracketCount = 1;
        let argStart = index + ".setUrlWithoutDomain(".length;
        let argEnd = -1;
        for (let i = argStart; i < body.length; i++) {
            if (body[i] === '(') bracketCount++;
            else if (body[i] === ')') {
                bracketCount--;
                if (bracketCount === 0) {
                    argEnd = i;
                    break;
                }
            }
        }
        if (argEnd !== -1) {
            const arg = body.substring(argStart, argEnd);
            const replacement = `.url = resolveUrl(${arg}, baseUrl)`;
            body = body.substring(0, index) + replacement + body.substring(argEnd + 1);
            index += replacement.length;
        } else {
            break;
        }
    }
    return body;
}

// Extract specific function body using bracket matching
function extractFunctionBody(funcName) {
    const startIdx = ktSource.indexOf(funcName);
    if (startIdx === -1) return null;

    let bracketCount = 0;
    let foundStart = false;
    let bodyStart = -1;
    let bodyEnd = -1;

    for (let i = startIdx; i < ktSource.length; i++) {
        if (ktSource[i] === '{') {
            if (!foundStart) {
                foundStart = true;
                bodyStart = i + 1;
            }
            bracketCount++;
        } else if (ktSource[i] === '}') {
            bracketCount--;
            if (foundStart && bracketCount === 0) {
                bodyEnd = i;
                break;
            }
        }
    }

    if (bodyStart !== -1 && bodyEnd !== -1) {
        return ktSource.substring(bodyStart, bodyEnd).trim();
    }
    return null;
}

function cleanKotlinToJs(body) {
    if (!body) return '';

    // Handle nested setUrlWithoutDomain first
    body = replaceSetUrlWithoutDomain(body);

    return body
        .replace(/val\s+/g, 'var ')
        .replace(/var\s+manga\s*=\s*SManga\.create\(\)/g, 'var manga = {}')
        .replace(/var\s+chapter\s*=\s*SChapter\.create\(\)/g, 'var chapter = {}')
        .replace(/\.title\s*=\s*/g, '.title = ')
        .replace(/\.thumbnail_url\s*=\s*/g, '.thumbnailUrl = ')
        .replace(/\.author\s*=\s*/g, '.author = ')
        .replace(/\.artist\s*=\s*/g, '.artist = ')
        .replace(/\.description\s*=\s*/g, '.description = ')
        .replace(/\.genre\s*=\s*/g, '.genres = ')
        .replace(/\.status\s*=\s*/g, '.status = ')
        .replace(/\.date_upload\s*=\s*/g, '.dateUpload = ')
        .replace(/\.chapter_number\s*=\s*/g, '.number = ')
        .replace(/\.scanlator\s*=\s*/g, '.scanlator = ')
        .replace(/return\s+manga/g, 'return { url: manga.url, title: manga.title, thumbnailUrl: manga.thumbnailUrl }')
        .replace(/return\s+chapter/g, 'return { url: chapter.url, title: chapter.title, number: chapter.number || 0.0, dateUpload: chapter.dateUpload || 0, scanlator: chapter.scanlator }')
        .replace(/absUrl\(([^)]+)\)/g, 'attr("abs:" + $1)')
        .replace(/\.text\(\)/g, '.text()')
        .replace(/\.attr\(([^)]+)\)/g, '.attr($1)')
        .replace(/element: Element/g, 'element')
        .replace(/document: Document/g, 'document')
        .replace(/override\s+fun\s+/g, 'function ')
        .replace(/:\s*SManga/g, '')
        .replace(/:\s*SChapter/g, '')
        .replace(/:\s*List<Page>/g, '')
        .replace(/SManga\.ONGOING/g, '"Ongoing"')
        .replace(/SManga\.COMPLETED/g, '"Completed"')
        .replace(/SManga\.HIATUS/g, '"Hiatus"')
        .replace(/SManga\.CANCELLED/g, '"Cancelled"')
        .replace(/SManga\.UNKNOWN/g, '"Unknown"')
        .replace(/:\s*Document/g, '')
        .replace(/:\s*Element/g, '')
        // Clean up float/long literals e.g. 1.0f -> 1.0, 0L -> 0
        .replace(/\b([0-9.]+)[fF]\b/g, '$1')
        .replace(/\b([0-9]+)[lL]\b/g, '$1')
        // Kotlin Elvis operator to JS OR
        .replace(/\?:\s*/g, '|| ')
        // Remove .toFloatOrNull() / .toIntOrNull()
        .replace(/\.(?:toFloat|toInt|toFloatOrNull|toIntOrNull|toFloatOrNull|toFloatOrNull)\(\)/g, '')
        // Page mapping
        .replace(/Page\([^,]+,\s*[^,]*,\s*([^)]+)\)/g, '$1')
        .replace(/\.mapIndexed\s*\{\s*([^,]+)\s*,\s*([^ ]+)\s*->\s*([^}]+)\}/g, '.map(function($2, $1) { return $3; })');
}

const popularFromElementBody = cleanKotlinToJs(extractFunctionBody('popularMangaFromElement'));
const searchFromElementBody = cleanKotlinToJs(extractFunctionBody('searchMangaFromElement')) || popularFromElementBody;
const detailsParseBody = cleanKotlinToJs(extractFunctionBody('mangaDetailsParse'));
const chapterFromElementBody = cleanKotlinToJs(extractFunctionBody('chapterFromElement'));
const pageListParseBody = cleanKotlinToJs(extractFunctionBody('pageListParse'));

// 4. Construct JS Output
const miniDomTemplate = `// --- Bundled MiniDOM & HTTP Polyfills for Sandboxed Execution ---
var baseUrl = "${baseUrl}";

function resolveUrl(relative, base) {
    if (!relative) return "";
    if (relative.indexOf("://") !== -1 || relative.indexOf("//") === 0) {
        if (relative.indexOf("//") === 0) return "https:" + relative;
        return relative;
    }
    var baseProto = "https://";
    var baseHost = base;
    if (base.indexOf("://") !== -1) {
        baseProto = base.split("://")[0] + "://";
        baseHost = base.split("://")[1];
    }
    baseHost = baseHost.split("/")[0];
    if (relative.indexOf("/") === 0) {
        return baseProto + baseHost + relative;
    }
    var basePat = base.split("?")[0];
    if (basePat.slice(-1) !== "/") {
        basePat = basePat.substring(0, basePat.lastIndexOf("/") + 1);
    }
    return basePat + relative;
}

function unescapeHTML(str) {
    return str
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&lt;/g, '<')
        .replace(/&gt;/g, '>')
        .replace(/&amp;/g, '&');
}

function parseSelector(selector) {
    var rawParts = selector.trim().split(/\\s+/);
    var parts = [];
    for (var i = 0; i < rawParts.length; i++) {
        var p = rawParts[i];
        var part = { tag: "", id: "", classes: [], attrs: [] };
        var attrReg = /\\[([a-zA-Z0-9:-]+)(?:([*^$]?=)(?:['"]([^'"]*)['"]|([^\\s\\]]+)))?\\]/g;
        var attrMatch;
        var cleanedP = p;
        while ((attrMatch = attrReg.exec(p)) !== null) {
            part.attrs.push({
                name: attrMatch[1].toLowerCase(),
                op: attrMatch[2] || "",
                value: attrMatch[3] || attrMatch[4] || ""
            });
            cleanedP = cleanedP.replace(attrMatch[0], "");
        }
        var tagMatch = cleanedP.match(/^([a-zA-Z0-9:-]+)/);
        if (tagMatch) {
            part.tag = tagMatch[1].toUpperCase();
            cleanedP = cleanedP.substring(tagMatch[1].length);
        }
        var classIdReg = /([.#])([a-zA-Z0-9_-]+)/g;
        var classIdMatch;
        while ((classIdMatch = classIdReg.exec(cleanedP)) !== null) {
            if (classIdMatch[1] === "#") {
                part.id = classIdMatch[2];
            } else {
                part.classes.push(classIdMatch[2].toLowerCase());
            }
        }
        parts.push(part);
    }
    return parts;
}

function querySelectorAll(node, selector) {
    var parts = parseSelector(selector);
    var results = [];
    function matchNode(n, selectorPart) {
        if (n.tagName === "#text") return false;
        if (selectorPart.tag && n.tagName !== selectorPart.tag) return false;
        if (selectorPart.id && n.attributes["id"] !== selectorPart.id) return false;
        if (selectorPart.classes) {
            var nodeClass = n.attributes["class"] || "";
            var nodeClasses = nodeClass.split(/\\s+/).map(function(c) { return c.toLowerCase(); });
            for (var i = 0; i < selectorPart.classes.length; i++) {
                if (nodeClasses.indexOf(selectorPart.classes[i]) === -1) return false;
            }
        }
        if (selectorPart.attrs) {
            for (var i = 0; i < selectorPart.attrs.length; i++) {
                var attr = selectorPart.attrs[i];
                var name = attr.name;
                var op = attr.op;
                var val = attr.value;
                if (!(name in n.attributes)) return false;
                if (op === "=" && n.attributes[name] !== val) return false;
                if (op === "*=" && n.attributes[name].indexOf(val) === -1) return false;
                if (op === "^=" && n.attributes[name].indexOf(val) !== 0) return false;
                if (op === "$=" && n.attributes[name].slice(-val.length) !== val) return false;
            }
        }
        return true;
    }
    function walk(n, partIndex, currentMatchList) {
        var part = parts[partIndex];
        var isLast = partIndex === parts.length - 1;
        var matches = [];
        function findDescendants(curr) {
            for (var i = 0; i < curr.children.length; i++) {
                var child = curr.children[i];
                if (matchNode(child, part)) {
                    matches.push(child);
                }
                findDescendants(child);
            }
        }
        findDescendants(n);
        if (isLast) {
            for (var i = 0; i < matches.length; i++) {
                if (currentMatchList.indexOf(matches[i]) === -1) {
                    currentMatchList.push(matches[i]);
                }
            }
        } else {
            for (var i = 0; i < matches.length; i++) {
                walk(matches[i], partIndex + 1, currentMatchList);
            }
        }
    }
    walk(node, 0, results);
    return results;
}

function wrapNode(n) {
    if (!n) return;
    n.attr = function(name) {
        if (name.indexOf("abs:") === 0) {
            var raw = this.attributes[name.substring(4).toLowerCase()] || "";
            return resolveUrl(raw, baseUrl);
        }
        return this.attributes[name.toLowerCase()] || "";
    };
    n.text = function() {
        return this.text;
    };
    n.select = function(sel) {
        var list = querySelectorAll(this, sel);
        for (var i = 0; i < list.length; i++) {
            wrapNode(list[i]);
        }
        return wrapList(list);
    };
    n.selectFirst = function(sel) {
        var list = this.select(sel);
        return list[0] || null;
    };
}

function wrapList(arr) {
    arr.first = function() {
        return this[0] || null;
    };
    arr.select = function(sel) {
        var results = [];
        for (var i = 0; i < this.length; i++) {
            var subList = this[i].select(sel);
            results = results.concat(subList);
        }
        return wrapList(results);
    };
    arr.attr = function(name) {
        return this[0] ? this[0].attr(name) : "";
    };
    arr.text = function() {
        return this[0] ? this[0].text() : "";
    };
    return arr;
}

function parseHTML(html) {
    var stack = [];
    var current = { tagName: "ROOT", attributes: {}, children: [], text: "" };
    stack.push(current);
    var tagReg = /<!--[\\s\\S]*?-->|<(?:\\/([a-zA-Z0-9:-]+)|([a-zA-Z0-9:-]+)([^>]*))>/g;
    var lastIndex = 0;
    var match;
    while ((match = tagReg.exec(html)) !== null) {
        var textSegment = html.substring(lastIndex, match.index);
        if (textSegment.trim()) {
            var textNode = { tagName: "#text", text: unescapeHTML(textSegment), children: [] };
            current.children.push(textNode);
            current.text += textNode.text;
        }
        if (match[0].indexOf("<!--") === 0) {
            // Comment
        } else if (match[1]) {
            // Close tag
            if (stack.length > 1) {
                var popped = stack.pop();
                current = stack[stack.length - 1];
                current.text += popped.text;
            }
        } else if (match[2]) {
            // Open tag
            var openTagName = match[2].toUpperCase();
            var attrStr = match[3] || "";
            var isSelfClosing = attrStr.trim().slice(-1) === "/" || ["IMG", "BR", "HR", "INPUT", "META", "LINK"].indexOf(openTagName) !== -1;
            var attributes = {};
            var attrReg = /([a-zA-Z0-9:-]+)(?:\\s*=\\s*(?:['"]([^'"]*)['"]|([^\\s>]+)))?/g;
            var attrMatch;
            while ((attrMatch = attrReg.exec(attrStr)) !== null) {
                attributes[attrMatch[1].toLowerCase()] = unescapeHTML(attrMatch[2] || attrMatch[3] || "");
            }
            var newNode = {
                tagName: openTagName,
                attributes: attributes,
                children: [],
                text: ""
            };
            current.children.push(newNode);
            if (!isSelfClosing) {
                stack.push(newNode);
                current = newNode;
            }
        }
        lastIndex = tagReg.lastIndex;
    }
    var remainingText = html.substring(lastIndex);
    if (remainingText.trim()) {
        current.children.push({ tagName: "#text", text: unescapeHTML(remainingText), children: [] });
        current.text += unescapeHTML(remainingText);
    }
    while (stack.length > 0) {
        var popped = stack.pop();
        if (stack.length > 0) {
            stack[stack.length - 1].text += popped.text;
        }
    }
    wrapNode(current);
    return current;
}
`;

const jsOutput = `// @name ${extensionName} Scraper
// @version 1.0.0
// @description Converted Ephyra JS Scraper for ${baseUrl}

${miniDomTemplate}

// --- Scraper Implementation ---

function discover(baseUrl) {
    return JSON.stringify({
        contentType: "MANGA",
        displayName: "${extensionName}"
    });
}

function search(payloadStr) {
    var payload = JSON.parse(payloadStr);
    var query = payload.query;
    var page = payload.page;

    var url = baseUrl + "/?s=" + encodeURIComponent(query) + "&page=" + page;
    if (!query || query.trim() === "") {
        url = baseUrl + "/popular?page=" + page;
    }

    var responseStr = http.get(url, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var document = parseHTML(responseStr);

    var items = document.select("${popularSelector}");
    var results = [];

    for (var i = 0; i < items.length; i++) {
        var element = items[i];
        var item = (function(element) {
            ${popularFromElementBody ? popularFromElementBody : `
            var url = element.select("a").first().attr("href");
            var title = element.select(".title").text() || element.text();
            var thumbnailUrl = element.select("img").first().attr("src");
            return { url: resolveUrl(url, baseUrl), title: title, thumbnailUrl: resolveUrl(thumbnailUrl, baseUrl) };
            `}
        })(element);
        results.push(item);
    }

    return JSON.stringify(results);
}

function getItem(url) {
    var responseStr = http.get(url, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var document = parseHTML(responseStr);

    ${detailsParseBody ? `
    // Transpiled Detail Parse
    var details = (function(document) {
        ${detailsParseBody}
    })(document);
    return JSON.stringify({
        url: url,
        title: details.title || document.select("h1").text(),
        description: details.description || "",
        thumbnailUrl: details.thumbnailUrl || "",
        status: details.status || "Unknown",
        contentType: "MANGA"
    });
    ` : `
    // Fallback Detail Parse
    var title = document.select("h1, .title").text();
    var description = document.select(".description, .summary").text();
    var cover = document.select(".cover img, img.manga-cover").first().attr("src");
    return JSON.stringify({
        url: url,
        title: title,
        description: description,
        thumbnailUrl: resolveUrl(cover, baseUrl),
        status: "Unknown",
        contentType: "MANGA"
    });
    `}
}

function getChapters(url) {
    var responseStr = http.get(url, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var document = parseHTML(responseStr);

    var items = document.select("${chapterSelector}");
    var results = [];

    for (var i = 0; i < items.length; i++) {
        var element = items[i];
        var chapter = (function(element) {
            ${chapterFromElementBody ? chapterFromElementBody : `
            var link = element.select("a").first();
            var url = link.attr("href");
            var title = link.text();
            var num = parseFloat(title.replace(/[^0-9.]/g, "")) || 0.0;
            return { url: resolveUrl(url, baseUrl), title: title, number: num, dateUpload: 0 };
            `}
        })(element);
        results.push(chapter);
    }

    return JSON.stringify(results);
}

function getPages(url) {
    var responseStr = http.get(url, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var document = parseHTML(responseStr);
    var results = [];

    ${pageListParseBody ? `
    // Transpiled Page List Parse
    var pages = (function(document) {
        ${pageListParseBody}
    })(document);
    // pages is an array of Page objects. Extract their URLs
    if (pages && pages.length) {
        for (var i = 0; i < pages.length; i++) {
            results.push(pages[i].url || pages[i]);
        }
    }
    ` : `
    // Fallback Page List Parse
    var imgs = document.select("div.page-break img, .reader img, img.manga-page");
    for (var i = 0; i < imgs.length; i++) {
        var src = imgs[i].attr("data-src") || imgs[i].attr("src");
        if (src) {
            results.push(resolveUrl(src, baseUrl));
        }
    }
    `}

    return JSON.stringify(results);
}
`;

const outputFileName = `${extensionName.toLowerCase().replace(/[^a-z0-9]/g, '_')}_scraper.js`;
const outputFilePath = path.join(path.dirname(ktFilePath), outputFileName);

fs.writeFileSync(outputFilePath, jsOutput, 'utf8');
console.log(`Success! Transpiled scraper script written to:\n  ${outputFilePath}`);
