import {result} from "../imports";


var data = result.data
var move = data.move[0]

var accuracy = move.accuracy
var power = move.power
var pp = move.pp
var cls = move.damageclass.name

if (cls === "physical") cls = "物理"; else if (cls === "special") cls = "特殊"; else if (cls === "status") cls = "状态";

var typeNames = {
    // en: move.type.names[0].name,
    // jp: move.type.names[1].name,
    zh: move.type.names[2].name
}

var names = {
    en: move.names[0].name,
    jp: move.names[1].name,
    zh: move.names[2].name,
}

var flavor = null
if (move.flavor.length > 0) flavor = move.flavor[0].flavor_text.replaceAll("\n", "")

var effect = null
if (move.moveeffect.text.length > 0) effect = move.moveeffect.text[0].effect.replaceAll("\n", "")

var moveLearnByPoke = []

for (var k in move.learn) {
    moveLearnByPoke.push({name: move.learn[k].pokemon.name, species_id: move.learn[k].pokemon.pokemon_species_id})
}

reply()
