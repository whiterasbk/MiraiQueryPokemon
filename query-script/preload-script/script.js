
var atSender = "`:atSender`"

var atAll = "`atAll`"

var repeat = "`:repeat`"

var senderName = "`:senderName`"

var senderNick = "`:senderNick`"

var senderRemark = "`:senderRemark`"

var senderNameCard = "`:senderNameCard`"

var senderSpecialTitle = "`:senderSpecialTitle`"

var groupName = "`:groupName`"

var time = "`sys.time`"

var jsdelivr_sprites_head = "https://cdn.jsdelivr.net/gh/PokeAPI/sprites@2.0.0/"

var github_raw_sprites_head = "https://raw.githubusercontent.com/PokeAPI/sprites/master/"

/*
* translation or mapping
* */
function trm(str) {
    var mapperResult = mainMapperIgnoreCase.invoke(str)
    if(mapperResult != null)
        return mapperResult
    else
        return translate(str)
}

function map(mapper, str) {
    var mapperResult = mapperIgnoreCase.invoke(str, mapper)
    if(mapperResult != null)
        return mapperResult
    else
        return str
}

function translate(str) {
    return translator.from(str)
}

function getSpritesUrl(pokemonId) {
    return pokeapi.getPokemon(pokemonId).sprites.frontDefault
}

function getFormSpritesUrl(form_id) {
    return pokeapi.getPokemonForm(form_id).sprites.frontDefault
}

function spritesUrl(idOrLink, isForm, tryJsd) {
    var link = null

    if (typeof idOrLink == "number") {
        if (typeof isForm == "boolean") {
            if (isForm) {
                link = getFormSpritesUrl(idOrLink)
            } else {
                link = getSpritesUrl(idOrLink)
            }
        } else if (isForm === "item") {
            link = pokeapi.getItem(item.id).sprites.default
        }
    } else if (typeof idOrLink == "string") {
        link = idOrLink
    } else link = idOrLink.toString()

    if (link === null) {
        link = "https://www.404.org/res/404"
        logger.error("没有返回可用的 spritesUrl")
    }

    if (localSpritesPath != null) {
        return transferUrl2Local(link, localSpritesPath)
    } else if (tryJsd === true) {
        return transferUrl2JsDelivr(link)
    } else {
        return link
    }
}

function transferUrl2Local(url, localPath) {
    return "file://" + localPath + "/" + url.substring(github_raw_sprites_head.length, url.length)
}

function transferUrl2JsDelivr(url) {
    return jsdelivr_sprites_head + url.substring(github_raw_sprites_head.length, url.length)
}

////

function getSpritesUrlByJsDelivr(pokemonId) {
    var url = getSpritesUrl(pokemonId)
    return jsdelivr_sprites_head + url.substring(github_raw_sprites_head.length, url.length)
}

function getSpritesUrlFromLocal(pokemonId) {
    var url = getSpritesUrl(pokemonId)
    return localSpritesPath + "/" + url.substring(github_raw_sprites_head.length, url.length)
}

function getFormSpritesUrlFromLocal(pokemonId) {
    var url = getFormSpritesUrl(pokemonId)
    return localSpritesPath + "/" + url.substring(github_raw_sprites_head.length, url.length)
}


////



function readDataFile(file) {
    return readDataFileKt.invoke(file)
}

function readJsonFromData(file) {
    return eval(readDataFile(file))
}

function image(url) {
    return "`:img " + url + "`"
}

function localImage(path) {
    return "`:img " + "file://" + path + "`"
}

function at(id) {
    return "`:at " + id + "`"
}

function reply() {
    return '`:reply`'
}



