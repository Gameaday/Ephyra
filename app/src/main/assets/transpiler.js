function transpile(ktSource, defaultName) {
    // 1. Extract metadata
    var nameMatch = ktSource.match(/val\s+name\s*=\s*"([^"]+)"/) || ktSource.match(/override\s+val\s+name\s*=\s*"([^"]+)"/);
    var baseUrlMatch = ktSource.match(/val\s+baseUrl\s*=\s*"([^"]+)"/) || ktSource.match(/override\s+val\s+baseUrl\s*=\s*"([^"]+)"/);

    var extensionName = nameMatch ? nameMatch[1] : defaultName;
    var baseUrl = baseUrlMatch ? baseUrlMatch[1] : 'https://example.com';

    // 2. Extract Selector strings
    function extractSelector(name) {
        var reg = new RegExp("override\\s+fun\\s+" + name + "\\s*\\(\\s*\\)\\s*(?::\\s*String\\s*)?=\\s*\"([^\"]+)\"");
        var match = ktSource.match(reg);
        if (match) return match[1];

        var regBlock = new RegExp("override\\s+fun\\s+" + name + "\\s*\\(\\s*\\)\\s*(?::\\s*String\\s*)?\\{\\s*return\\s*\"([^\"]+)\"\\s*\\}");
        var matchBlock = ktSource.match(regBlock);
        return matchBlock ? matchBlock[1] : "";
    }

    var popularSelector = extractSelector('popularMangaSelector') || 'div.item';
    var popularNextPage = extractSelector('popularMangaNextPageSelector') || 'a.next-page';
    var searchSelector = extractSelector('searchMangaSelector') || popularSelector || 'div.item';
    var searchNextPage = extractSelector('searchMangaNextPageSelector') || popularNextPage || 'a.next-page';
    var chapterSelector = extractSelector('chapterListSelector') || 'div.chapter';

    // Helper to handle balanced parentheses for setUrlWithoutDomain
    function replaceSetUrlWithoutDomain(body) {
        var index = 0;
        while ((index = body.indexOf(".setUrlWithoutDomain(", index)) !== -1) {
            var bracketCount = 1;
            var argStart = index + ".setUrlWithoutDomain(".length;
            var argEnd = -1;
            for (var i = argStart; i < body.length; i++) {
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
                var arg = body.substring(argStart, argEnd);
                var replacement = ".url = resolveUrl(" + arg + ", baseUrl)";
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
        var startIdx = ktSource.indexOf(funcName);
        if (startIdx === -1) return null;

        var bracketCount = 0;
        var foundStart = false;
        var bodyStart = -1;
        var bodyEnd = -1;

        for (var i = startIdx; i < ktSource.length; i++) {
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
            .replace(/\.(?:toFloat|toInt|toFloatOrNull|toIntOrNull)\(\)/g, '')
            // Page mapping
            .replace(/Page\([^,]+,\s*[^,]*,\s*([^)]+)\)/g, '$1')
            .replace(/\.mapIndexed\s*\{\s*([^,]+)\s*,\s*([^ ]+)\s*->\s*([^}]+)\}/g, '.map(function($2, $1) { return $3; })');
    }

    var popularFromElementBody = cleanKotlinToJs(extractFunctionBody('popularMangaFromElement'));
    var searchFromElementBody = cleanKotlinToJs(extractFunctionBody('searchMangaFromElement')) || popularFromElementBody;
    var detailsParseBody = cleanKotlinToJs(extractFunctionBody('mangaDetailsParse'));
    var chapterFromElementBody = cleanKotlinToJs(extractFunctionBody('chapterFromElement'));
    var pageListParseBody = cleanKotlinToJs(extractFunctionBody('pageListParse'));

    // Inject MiniDOM
    var miniDomTemplate = getMiniDomTemplate(baseUrl);

    var jsOutput = "// @name " + extensionName + " Scraper\n" +
        "// @version 1.0.0\n" +
        "// @description Converted Ephyra JS Scraper for " + baseUrl + "\n\n" +
        miniDomTemplate + "\n\n" +
        "// --- Scraper Implementation ---\n\n" +
        "function discover(baseUrl) {\n" +
        "    return JSON.stringify({\n" +
        "        contentType: \"MANGA\",\n" +
        "        displayName: \"" + extensionName + "\"\n" +
        "    });\n" +
        "}\n\n" +
        "function search(payloadStr) {\n" +
        "    var payload = JSON.parse(payloadStr);\n" +
        "    var query = payload.query;\n" +
        "    var page = payload.page;\n\n" +
        "    var url = baseUrl + \"/?s=\" + encodeURIComponent(query) + \"&page=\" + page;\n" +
        "    if (!query || query.trim() === \"\") {\n" +
        "        url = baseUrl + \"/popular?page=\" + page;\n" +
        "    }\n\n" +
        "    var responseStr = http.get(url, JSON.stringify({ \"User-Agent\": \"Ephyra/1.0\" }));\n" +
        "    var document = parseHTML(responseStr);\n\n" +
        "    var items = document.select(\"" + popularSelector + "\");\n" +
        "    var results = [];\n\n" +
        "    for (var i = 0; i < items.length; i++) {\n" +
        "        var element = items[i];\n" +
        "        var item = (function(element) {\n" +
        (popularFromElementBody ? popularFromElementBody : 
        "            var url = element.select(\"a\").first().attr(\"href\");\n" +
        "            var title = element.select(\".title\").text() || element.text();\n" +
        "            var thumbnailUrl = element.select(\"img\").first().attr(\"src\");\n" +
        "            return { url: resolveUrl(url, baseUrl), title: title, thumbnailUrl: resolveUrl(thumbnailUrl, baseUrl) };\n") +
        "        })(element);\n" +
        "        results.push(item);\n" +
        "    }\n\n" +
        "    return JSON.stringify(results);\n" +
        "}\n\n" +
        "function getItem(url) {\n" +
        "    var responseStr = http.get(url, JSON.stringify({ \"User-Agent\": \"Ephyra/1.0\" }));\n" +
        "    var document = parseHTML(responseStr);\n\n" +
        (detailsParseBody ? 
        "    var details = (function(document) {\n" + detailsParseBody + "\n    })(document);\n" +
        "    return JSON.stringify({\n" +
        "        url: url,\n" +
        "        title: details.title || document.select(\"h1\").text(),\n" +
        "        description: details.description || \"\",\n" +
        "        thumbnailUrl: details.thumbnailUrl || \"\",\n" +
        "        status: details.status || \"Unknown\",\n" +
        "        contentType: \"MANGA\"\n" +
        "    });\n" :
        "    var title = document.select(\"h1, .title\").text();\n" +
        "    var description = document.select(\".description, .summary\").text();\n" +
        "    var cover = document.select(\".cover img, img.manga-cover\").first().attr(\"src\");\n" +
        "    return JSON.stringify({\n" +
        "        url: url,\n" +
        "        title: title,\n" +
        "        description: description,\n" +
        "        thumbnailUrl: resolveUrl(cover, baseUrl),\n" +
        "        status: \"Unknown\",\n" +
        "        contentType: \"MANGA\"\n" +
        "    });\n") +
        "}\n\n" +
        "function getChapters(url) {\n" +
        "    var responseStr = http.get(url, JSON.stringify({ \"User-Agent\": \"Ephyra/1.0\" }));\n" +
        "    var document = parseHTML(responseStr);\n\n" +
        "    var items = document.select(\"" + chapterSelector + "\");\n" +
        "    var results = [];\n\n" +
        "    for (var i = 0; i < items.length; i++) {\n" +
        "        var element = items[i];\n" +
        "        var chapter = (function(element) {\n" +
        (chapterFromElementBody ? chapterFromElementBody :
        "            var link = element.select(\"a\").first();\n" +
        "            var url = link.attr(\"href\");\n" +
        "            var title = link.text();\n" +
        "            var num = parseFloat(title.replace(/[^0-9.]/g, \"\")) || 0.0;\n" +
        "            return { url: resolveUrl(url, baseUrl), title: title, number: num, dateUpload: 0 };\n") +
        "        })(element);\n" +
        "        results.push(chapter);\n" +
        "    }\n\n" +
        "    return JSON.stringify(results);\n" +
        "}\n\n" +
        "function getPages(url) {\n" +
        "    var responseStr = http.get(url, JSON.stringify({ \"User-Agent\": \"Ephyra/1.0\" }));\n" +
        "    var document = parseHTML(responseStr);\n" +
        "    var results = [];\n\n" +
        (pageListParseBody ?
        "    var pages = (function(document) {\n" + pageListParseBody + "\n    })(document);\n" +
        "    if (pages && pages.length) {\n" +
        "        for (var i = 0; i < pages.length; i++) {\n" +
        "            results.push(pages[i].url || pages[i]);\n" +
        "        }\n" +
        "    }\n" :
        "    var imgs = document.select(\"div.page-break img, .reader img, img.manga-page\");\n" +
        "    for (var i = 0; i < imgs.length; i++) {\n" +
        "        var src = imgs[i].attr(\"data-src\") || imgs[i].attr(\"src\");\n" +
        "        if (src) {\n" +
        "            results.push(resolveUrl(src, baseUrl));\n" +
        "        }\n" +
        "    }\n") +
        "\n    return JSON.stringify(results);\n" +
        "}\n";

    return jsOutput;
}

function getMiniDomTemplate(baseUrl) {
    return "var baseUrl = \"" + baseUrl + "\";\n" +
        "function resolveUrl(relative, base) {\n" +
        "    if (!relative) return \"\";\n" +
        "    if (relative.indexOf(\"://\") !== -1 || relative.indexOf(\"//\") === 0) {\n" +
        "        if (relative.indexOf(\"//\") === 0) return \"https:\" + relative;\n" +
        "        return relative;\n" +
        "    }\n" +
        "    var baseProto = \"https://\";\n" +
        "    var baseHost = base;\n" +
        "    if (base.indexOf(\"://\") !== -1) {\n" +
        "        baseProto = base.split(\"://\")[0] + \"://\";\n" +
        "        baseHost = base.split(\"://\")[1];\n" +
        "    }\n" +
        "    baseHost = baseHost.split(\"/\")[0];\n" +
        "    if (relative.indexOf(\"/\") === 0) {\n" +
        "        return baseProto + baseHost + relative;\n" +
        "    }\n" +
        "    var basePat = base.split(\"?\")[0];\n" +
        "    if (basePat.slice(-1) !== \"/\") {\n" +
        "        basePat = basePat.substring(0, basePat.lastIndexOf(\"/\") + 1);\n" +
        "    }\n" +
        "    return basePat + relative;\n" +
        "}\n" +
        "function unescapeHTML(str) {\n" +
        "    return str.replace(/&quot;/g, '\"').replace(/&apos;/g, \"'\").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');\n" +
        "}\n" +
        "function parseSelector(selector) {\n" +
        "    var rawParts = selector.trim().split(/\\s+/);\n" +
        "    var parts = [];\n" +
        "    for (var i = 0; i < rawParts.length; i++) {\n" +
        "        var p = rawParts[i];\n" +
        "        var part = { tag: \"\", id: \"\", classes: [], attrs: [] };\n" +
        "        var attrReg = /\\[([a-zA-Z0-9:-]+)(?:([*^$]?=)(?:['\"]([^'\"]*)['\"]|([^\\s\\]]+)))?\\]/g;\n" +
        "        var attrMatch;\n" +
        "        var cleanedP = p;\n" +
        "        while ((attrMatch = attrReg.exec(p)) !== null) {\n" +
        "            part.attrs.push({\n" +
        "                name: attrMatch[1].toLowerCase(),\n" +
        "                op: attrMatch[2] || \"\",\n" +
        "                value: attrMatch[3] || attrMatch[4] || \"\"\n" +
        "            });\n" +
        "            cleanedP = cleanedP.replace(attrMatch[0], \"\");\n" +
        "        }\n" +
        "        var tagMatch = cleanedP.match(/^([a-zA-Z0-9:-]+)/);\n" +
        "        if (tagMatch) {\n" +
        "            part.tag = tagMatch[1].toUpperCase();\n" +
        "            cleanedP = cleanedP.substring(tagMatch[1].length);\n" +
        "        }\n" +
        "        var classIdReg = /([.#])([a-zA-Z0-9_-]+)/g;\n" +
        "        var classIdMatch;\n" +
        "        while ((classIdMatch = classIdReg.exec(cleanedP)) !== null) {\n" +
        "            if (classIdMatch[1] === \"#\") {\n" +
        "                part.id = classIdMatch[2];\n" +
        "            } else {\n" +
        "                part.classes.push(classIdMatch[2].toLowerCase());\n" +
        "            }\n" +
        "        }\n" +
        "        parts.push(part);\n" +
        "    }\n" +
        "    return parts;\n" +
        "}\n" +
        "function querySelectorAll(node, selector) {\n" +
        "    var parts = parseSelector(selector);\n" +
        "    var results = [];\n" +
        "    function matchNode(n, selectorPart) {\n" +
        "        if (n.tagName === \"#text\") return false;\n" +
        "        if (selectorPart.tag && n.tagName !== selectorPart.tag) return false;\n" +
        "        if (selectorPart.id && n.attributes[\"id\"] !== selectorPart.id) return false;\n" +
        "        if (selectorPart.classes) {\n" +
        "            var nodeClass = n.attributes[\"class\"] || \"\";\n" +
        "            var nodeClasses = nodeClass.split(/\\s+/).map(function(c) { return c.toLowerCase(); });\n" +
        "            for (var i = 0; i < selectorPart.classes.length; i++) {\n" +
        "                if (nodeClasses.indexOf(selectorPart.classes[i]) === -1) return false;\n" +
        "            }\n" +
        "        }\n" +
        "        if (selectorPart.attrs) {\n" +
        "            for (var i = 0; i < selectorPart.attrs.length; i++) {\n" +
        "                var attr = selectorPart.attrs[i];\n" +
        "                var name = attr.name;\n" +
        "                var op = attr.op;\n" +
        "                var val = attr.value;\n" +
        "                if (!(name in n.attributes)) return false;\n" +
        "                if (op === \"=\" && n.attributes[name] !== val) return false;\n" +
        "                if (op === \"*=\" && n.attributes[name].indexOf(val) === -1) return false;\n" +
        "                if (op === \"^=\" && n.attributes[name].indexOf(val) !== 0) return false;\n" +
        "                if (op === \"$=\" && n.attributes[name].slice(-val.length) !== val) return false;\n" +
        "            }\n" +
        "        }\n" +
        "        return true;\n" +
        "    }\n" +
        "    function walk(n, partIndex, currentMatchList) {\n" +
        "        var part = parts[partIndex];\n" +
        "        var isLast = partIndex === parts.length - 1;\n" +
        "        var matches = [];\n" +
        "        function findDescendants(curr) {\n" +
        "            for (var i = 0; i < curr.children.length; i++) {\n" +
        "                var child = curr.children[i];\n" +
        "                if (matchNode(child, part)) matches.push(child);\n" +
        "                findDescendants(child);\n" +
        "            }\n" +
        "        }\n" +
        "        findDescendants(n);\n" +
        "        if (isLast) {\n" +
        "            for (var i = 0; i < matches.length; i++) {\n" +
        "                if (currentMatchList.indexOf(matches[i]) === -1) currentMatchList.push(matches[i]);\n" +
        "            }\n" +
        "        } else {\n" +
        "            for (var i = 0; i < matches.length; i++) walk(matches[i], partIndex + 1, currentMatchList);\n" +
        "        }\n" +
        "    }\n" +
        "    walk(node, 0, results);\n" +
        "    return results;\n" +
        "}\n" +
        "function wrapNode(n) {\n" +
        "    if (!n) return;\n" +
        "    n.attr = function(name) {\n" +
        "        if (name.indexOf(\"abs:\") === 0) {\n" +
        "            var raw = this.attributes[name.substring(4).toLowerCase()] || \"\";\n" +
        "            return resolveUrl(raw, baseUrl);\n" +
        "        }\n" +
        "        return this.attributes[name.toLowerCase()] || \"\";\n" +
        "    };\n" +
        "    n.text = function() { return this.text; };\n" +
        "    n.select = function(sel) {\n" +
        "        var list = querySelectorAll(this, sel);\n" +
        "        for (var i = 0; i < list.length; i++) wrapNode(list[i]);\n" +
        "        return wrapList(list);\n" +
        "    };\n" +
        "    n.selectFirst = function(sel) {\n" +
        "        var list = this.select(sel);\n" +
        "        return list[0] || null;\n" +
        "    };\n" +
        "}\n" +
        "function wrapList(arr) {\n" +
        "    arr.first = function() { return this[0] || null; };\n" +
        "    arr.select = function(sel) {\n" +
        "        var results = [];\n" +
        "        for (var i = 0; i < this.length; i++) {\n" +
        "            var subList = this[i].select(sel);\n" +
        "            results = results.concat(subList);\n" +
        "        }\n" +
        "        return wrapList(results);\n" +
        "    };\n" +
        "    arr.attr = function(name) { return this[0] ? this[0].attr(name) : \"\"; };\n" +
        "    arr.text = function() { return this[0] ? this[0].text() : \"\"; };\n" +
        "    return arr;\n" +
        "}\n" +
        "function parseHTML(html) {\n" +
        "    var stack = [];\n" +
        "    var current = { tagName: \"ROOT\", attributes: {}, children: [], text: \"\" };\n" +
        "    stack.push(current);\n" +
        "    var tagReg = /<!--[\\s\\S]*?-->|<(?:\\/([a-zA-Z0-9:-]+)|([a-zA-Z0-9:-]+)([^>]*))>/g;\n" +
        "    var lastIndex = 0;\n" +
        "    var match;\n" +
        "    while ((match = tagReg.exec(html)) !== null) {\n" +
        "        var textSegment = html.substring(lastIndex, match.index);\n" +
        "        if (textSegment.trim()) {\n" +
        "            var textNode = { tagName: \"#text\", text: unescapeHTML(textSegment), children: [] };\n" +
        "            current.children.push(textNode);\n" +
        "            current.text += textNode.text;\n" +
        "        }\n" +
        "        if (match[0].indexOf(\"<!--\") === 0) {\n" +
        "        } else if (match[1]) {\n" +
        "            if (stack.length > 1) {\n" +
        "                var popped = stack.pop();\n" +
        "                current = stack[stack.length - 1];\n" +
        "                current.text += popped.text;\n" +
        "            }\n" +
        "        } else if (match[2]) {\n" +
        "            var openTagName = match[2].toUpperCase();\n" +
        "            var attrStr = match[3] || \"\";\n" +
        "            var isSelfClosing = attrStr.trim().slice(-1) === \"/\" || [\"IMG\", \"BR\", \"HR\", \"INPUT\", \"META\", \"LINK\"].indexOf(openTagName) !== -1;\n" +
        "            var attributes = {};\n" +
        "            var attrReg = /([a-zA-Z0-9:-]+)(?:\\s*=\\s*(?:['\"]([^'\"]*)['\"]|([^\\s>]+)))?/g;\n" +
        "            var attrMatch;\n" +
        "            while ((attrMatch = attrReg.exec(attrStr)) !== null) {\n" +
        "                attributes[attrMatch[1].toLowerCase()] = unescapeHTML(attrMatch[2] || attrMatch[3] || \"\");\n" +
        "            }\n" +
        "            var newNode = { tagName: openTagName, attributes: attributes, children: [], text: \"\" };\n" +
        "            current.children.push(newNode);\n" +
        "            if (!isSelfClosing) {\n" +
        "                stack.push(newNode);\n" +
        "                current = newNode;\n" +
        "            }\n" +
        "        }\n" +
        "        lastIndex = tagReg.lastIndex;\n" +
        "    }\n" +
        "    var remainingText = html.substring(lastIndex);\n" +
        "    if (remainingText.trim()) {\n" +
        "        current.children.push({ tagName: \"#text\", text: unescapeHTML(remainingText), children: [] });\n" +
        "        current.text += unescapeHTML(remainingText);\n" +
        "    }\n" +
        "    while (stack.length > 0) {\n" +
        "        var popped = stack.pop();\n" +
        "        if (stack.length > 0) stack[stack.length - 1].text += popped.text;\n" +
        "    }\n" +
        "    wrapNode(current);\n" +
        "    return current;\n" +
        "}\n" +
        "return parseHTML;\n";
}
