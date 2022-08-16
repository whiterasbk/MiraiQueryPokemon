var output = null
var names = readDataJSON.invoke("cache-data/pokemon-names-official.json")
var pass = null

if (names.has(input.get('pokemonId'))) {
    output = input
    var name = names.get(input.get('pokemonId'))

    if (input.has("pkName")) {
        pass = input.get('pkName') // 传递 form index
    }
    output.put("pkName", "%" + name + "%")
} else {
    throw new java.lang.Exception("not found name by given id: " + input.get('pokemonId'))
}
