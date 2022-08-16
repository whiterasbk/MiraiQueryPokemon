import {result} from "../imports";


var data = result.data
var ability = data.ability[0]
var species = data.species

var names = {
    en: ability.names[0].name,
    jp: ability.names[1].name,
    zh: ability.names[2].name,
}

var flavor = null
if (ability.flavor.length > 0) flavor = ability.flavor[0].flavor_text.replaceAll("\n", "")

var effect = null
if (ability.effecttext.length > 0) effect = ability.effecttext[0].effect.replaceAll("\n", "")

var hasAbility = []

for (var i in species) {
    hasAbility.push(species[i].names[2].name)
}



reply()
