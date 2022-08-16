
import '../preload-script/script'
import '../imports'
import {extraArgument, logger, mappers, pokeapi, push, result} from "../imports"

var arg = extraArgument
var data = result.data

// 宝可梦种类
var species = data.species[0]
var pokemon = data.pokemon[0]
var forms = data.form

// 宝可梦 stat
var stats = {}
// 宝可梦 特性
var abilities = []
// 宝可梦 属性
var types = []
// 宝可梦 蛋组
var egg_group_name = []
var egg_group_zh_name = []
initialEgg_group(species.egg_groups)

var hasFormTips = ""
var formTips = null
// 宝可梦图片
var frontSide = ""

if (forms.length > 1 && pass == null) {
    hasFormTips = "\n该宝可梦有"+forms.length+"种形态, 请添加参数查看不同形态"
}

if (pass != null) {
    var formIndex = pass - 1
    if (forms[formIndex] === undefined) throw new java.lang.Exception("index: " + formIndex + " 越界")
    var id = forms[formIndex].id
    var formName = forms[formIndex].form_name
    // var pokemon_id = forms[formIndex].pokemon_id
    // var form_order = forms[formIndex].form_order

    var connector = ""
    if (formName !== "") connector = "-"
    //
    // formTips = "\n形态: " + arg.get('pkName').replaceAll("%", "") + connector + formName + ", " +
    //     (formIndex + 1) + ", id: " + id + ", pid: " + pokemon_id + ", order: " + form_order
    formTips = "\n形态名: " + arg.get('pkName').replaceAll("%", "") + connector + formName

    var pokemon_other_form = forms[formIndex].pokemon
    initialStats(pokemon_other_form.stats)
    initialAbilities(pokemon_other_form.abilities)
    initialType(pokemon_other_form.types)
    frontSide = spritesUrl(id, true, true)

} else {
    initialStats(pokemon.stats)
    initialAbilities(pokemon.abilities)
    initialType(pokemon.types)
    frontSide = spritesUrl(arg.get('pokemonId'), false, true)
}

function initialEgg_group(_egg_group) {
    for (var i in _egg_group) {
        egg_group_name.push(_egg_group[i].item.name)
    }

    for (var i in egg_group_name) {
        egg_group_zh_name.push(mappers.egg_group[egg_group_name[i]])
    }
}

function initialAbilities(_abilities) {
    for(var j in _abilities) {
        // if (_abilities[j].ability[0].names.length > 0)
        abilities.push({
            name: _abilities[j].ability.names[0].name,
            hidden: _abilities[j].is_hidden
        })
    }
}

function initialType(_types) {
    for(var i in _types) {
        // if (_types[i].type.length > 0)
        types.push(_types[i].type.names[0].name)
    }
}

function initialStats(_stats) {
    for (var i in _stats) {
        stats[_stats[i]['stat']['name']] = {
            base: _stats[i]['base_stat'],
            effort: _stats[i]['effort']
            // name: _stats[i]['stat']['name']
        }
    }
}

function no_hidden_ability() {
    var ret = []
    for (var i in abilities) {
        if (!abilities[i].hidden) ret.push(abilities[i].name)
    }
    return ret
}

function hidden_ability() {
    var ret = []
    for (var i in abilities) {
        if (abilities[i].hidden) ret.push(abilities[i].name)
    }
    return ret
}

function calcEffort() {
    for (var i in stats) {
        if (stats[i].effort > 0) {
            push(mappers['stats_abbreviation'][i] + "+" + stats[i].effort + " ")
        }
    }
}

function calclv(attr, level) {
    if (attr === "hp") {
        return {
            min: Math.floor(((stats.hp.base * 2 + 0 + 0/4) * level)/100 + 10 + level),
            max: Math.floor(((stats.hp.base * 2 + 31 + 252/4) * level)/100 + 10 + level)
        }
    } else {
        return {
            min: Math.floor((((stats[attr].base * 2 + 0 + 0/4) * level)/100+5)*0.9),
            max: Math.floor((((stats[attr].base * 2 + 31 + 252/4) * level)/100+5)*1.1),
        }
    }
}

var img = image(frontSide)

reply()