import {pokeapi, result} from "../imports";

var data = result.data
var item = data.item[0]

var effect = null
var flavor = null
if (item.effect.length !== 0) effect = item.effect[0].effect.trim().replaceAll("\n", "")
if (item.flavor.length !== 0) flavor = item.flavor[0].flavor_text.trim().replaceAll("\n", "")

var name = {
    en: item.names[0].name,
    jp: item.names[1].name,
    zh: item.names[2].name
}

var img =  image(spritesUrl(item.id, "item", true))

reply()
