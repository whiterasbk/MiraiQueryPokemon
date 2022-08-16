// var map = require("public.yml")

// map["kt"] = "kotlin"
var pokemonNames = fuzzyQueryData['pokemon-names']

for (var id in pokemonNames) {
    var localName = pokemonNames[id]
    mappers['main'][localName['enName']] = localName['zhName']
}