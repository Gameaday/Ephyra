// @name MangaDex Scraper
// @version 1.0.0
// @description Sandboxed QuickJS scraper for mangadex.org using the official MangaDex API.

function discover(baseUrl) {
    return JSON.stringify({
        contentType: "MANGA",
        displayName: "MangaDex"
    });
}

function search(payloadStr) {
    var payload = JSON.parse(payloadStr);
    var query = payload.query;
    var page = payload.page;
    
    var limit = 20;
    var offset = (page - 1) * limit;
    
    var url = "https://api.mangadex.org/manga?limit=" + limit + "&offset=" + offset + "&includes[]=cover_art";
    if (query && query.trim() !== "") {
        url += "&title=" + encodeURIComponent(query);
    } else {
        // Fallback to popular (most followed)
        url += "&order[followedCount]=desc";
    }
    
    var responseStr = http.get(url, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var response = JSON.parse(responseStr);
    
    var results = [];
    if (response && response.data) {
        for (var i = 0; i < response.data.length; i++) {
            var item = response.data[i];
            
            // Extract cover art filename
            var coverFileName = "";
            if (item.relationships) {
                for (var j = 0; j < item.relationships.length; j++) {
                    var rel = item.relationships[j];
                    if (rel.type === "cover_art" && rel.attributes) {
                        coverFileName = rel.attributes.fileName;
                        break;
                    }
                }
            }
            
            var title = "Untitled Manga";
            if (item.attributes && item.attributes.title) {
                title = item.attributes.title.en || 
                        item.attributes.title.ja || 
                        item.attributes.title["ja-ro"] || 
                        Object.values(item.attributes.title)[0] || 
                        title;
            }
            
            var coverUrl = coverFileName ? "https://uploads.mangadex.org/covers/" + item.id + "/" + coverFileName : "";
            
            results.push({
                url: "https://mangadex.org/title/" + item.id,
                title: title,
                thumbnailUrl: coverUrl,
                status: "Unknown",
                contentType: "MANGA"
            });
        }
    }
    
    return JSON.stringify(results);
}

function getItem(url) {
    var parts = url.split("/");
    var id = parts[parts.length - 1];
    if (!id) id = parts[parts.length - 2];
    
    var apiUrl = "https://api.mangadex.org/manga/" + id + "?includes[]=cover_art";
    var responseStr = http.get(apiUrl, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var response = JSON.parse(responseStr);
    
    if (!response || !response.data) {
        throw new Error("Failed to get details for manga ID " + id);
    }
    
    var item = response.data;
    
    // Extract cover art filename
    var coverFileName = "";
    if (item.relationships) {
        for (var j = 0; j < item.relationships.length; j++) {
            var rel = item.relationships[j];
            if (rel.type === "cover_art" && rel.attributes) {
                coverFileName = rel.attributes.fileName;
                break;
            }
        }
    }
    
    var title = "Untitled Manga";
    var description = "";
    var status = "Unknown";
    
    if (item.attributes) {
        if (item.attributes.title) {
            title = item.attributes.title.en || 
                    item.attributes.title.ja || 
                    item.attributes.title["ja-ro"] || 
                    Object.values(item.attributes.title)[0] || 
                    title;
        }
        if (item.attributes.description) {
            description = item.attributes.description.en || 
                          Object.values(item.attributes.description)[0] || 
                          "";
        }
        if (item.attributes.status) {
            var s = item.attributes.status.toLowerCase();
            if (s === "ongoing") status = "Ongoing";
            else if (s === "completed") status = "Completed";
            else if (s === "hiatus") status = "Hiatus";
            else if (s === "cancelled") status = "Cancelled";
        }
    }
    
    var coverUrl = coverFileName ? "https://uploads.mangadex.org/covers/" + item.id + "/" + coverFileName : "";
    
    return JSON.stringify({
        url: url,
        title: title,
        description: description,
        thumbnailUrl: coverUrl,
        status: status,
        contentType: "MANGA"
    });
}

function getChapters(url) {
    var parts = url.split("/");
    var id = parts[parts.length - 1];
    if (!id) id = parts[parts.length - 2];
    
    // Retrieve first 500 chapters translated to English
    var feedUrl = "https://api.mangadex.org/manga/" + id + "/feed?limit=500&translatedLanguage[]=en&order[chapter]=desc";
    var responseStr = http.get(feedUrl, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var response = JSON.parse(responseStr);
    
    var chapters = [];
    if (response && response.data) {
        for (var i = 0; i < response.data.length; i++) {
            var chap = response.data[i];
            
            var chapterNum = parseFloat(chap.attributes.chapter) || 0.0;
            var chTitle = chap.attributes.title ? " - " + chap.attributes.title : "";
            var title = "Chapter " + chap.attributes.chapter + chTitle;
            
            var dateUpload = 0;
            if (chap.attributes.publishAt) {
                try {
                    dateUpload = Date.parse(chap.attributes.publishAt);
                } catch(e) {}
            }
            
            chapters.push({
                url: "https://mangadex.org/chapter/" + chap.id,
                title: title,
                number: chapterNum,
                dateUpload: dateUpload
            });
        }
    }
    
    return JSON.stringify(chapters);
}

function getPages(url) {
    var parts = url.split("/");
    var id = parts[parts.length - 1];
    if (!id) id = parts[parts.length - 2];
    
    var serverUrl = "https://api.mangadex.org/at-home/server/" + id;
    var responseStr = http.get(serverUrl, JSON.stringify({ "User-Agent": "Ephyra/1.0" }));
    var response = JSON.parse(responseStr);
    
    var pages = [];
    if (response && response.chapter && response.baseUrl) {
        var baseUrl = response.baseUrl;
        var hash = response.chapter.hash;
        var files = response.chapter.data;
        
        for (var i = 0; i < files.length; i++) {
            pages.push(baseUrl + "/data/" + hash + "/" + files[i]);
        }
    }
    
    return JSON.stringify(pages);
}
