
import '../preload-script/script'
import '../imports'
import {extraArgument, logger, mappers, pokeapi, push, result} from "../imports"

var arg = extraArgument
var data = result.data

// 宝可梦种类
var species = data.species[0]
var pokemon = data.pokemon[0]
var forms = data.form

// 宝可梦能学会的招式
var moves_levelup = []
var moves_machine = []
var moves_egg = []
var moves_tutor = []
var moves_form_change = []

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

    var connector = ""
    if (formName !== "") connector = "-"
    formTips = "\n形态名: " + arg.get('pkName').replaceAll("%", "") + connector + formName

    var pokemon_other_form = forms[formIndex].pokemon

    initialMoves(pokemon_other_form.moves_levelup, moves_levelup)
    initialMoves(pokemon_other_form.moves_machine, moves_machine)
    initialMoves(pokemon_other_form.moves_egg, moves_egg)
    initialMoves(pokemon_other_form.moves_tutor, moves_tutor)
    initialMoves(pokemon_other_form.moves_form_change, moves_form_change)

    frontSide = spritesUrl(id, true, true)
} else {

    initialMoves(pokemon.moves_levelup, moves_levelup)
    initialMoves(pokemon.moves_machine, moves_machine)
    initialMoves(pokemon.moves_egg, moves_egg)
    initialMoves(pokemon.moves_tutor, moves_tutor)
    initialMoves(pokemon.moves_form_change, moves_form_change)

    frontSide = spritesUrl(arg.get('pokemonId'), false, true)
}

var img = image(frontSide)

function initialMoves(_moves, desc) {
    for(var i in _moves) {
        var item = _moves[i]
        var learnMachines = []

        for (var r in item.move.machines) {
            learnMachines.push(item.move.machines[r].item.name)
        }

        desc.push({
            id: item.id,
            names: {
                en: item.move.names[0].name,
                jp: item.move.names[1].name,
                zh: item.move.names[2].name
            },
            level: item.level,
            learnMethod: item.movelearnmethod.name,
            learnMachines: learnMachines,
            move_learn_method_id: item.move.move_learn_method_id
        })
    }
}

function levelup() {
    if (moves_levelup.length === 0) {
        push("")
        return
    }

    push("# 升级: 序号. 等级 - 技能 \n")

    var sorted = moves_levelup.sort(function (a, b) {
        return a.level - b.level
    })

    var count = 0
    for (var i in sorted) {
        count ++
        var move = sorted[i]
        push(count + ". lv" + move.level + " - " + move.names.zh + "\n")
    }
}

function tutor() {
    if (moves_tutor.length === 0) {
        push("")
        return
    }

    push("# 教学: 序号. 技能 \n")

    var count = 0
    for (var i in moves_tutor) {

        if (moves_tutor[i].learnMachines.length > 0) continue

        count ++
        var move = moves_tutor[i]
        push(count + ". " + move.names.zh + "\n")
    }
}

function egg() {
    if (moves_egg.length === 0) {
        push("")
        return
    }

    push("# 蛋招式: 序号. 技能 \n")

    var count = 0
    for (var i in moves_egg) {
        count ++
        var move = moves_egg[i]
        push(count + ". " + move.names.zh + "\n")
    }
}

function form_change() {
    if (moves_form_change.length === 0) {
        push("")
        return
    }

    push("# 形态转化: 序号. 技能 \n")

    var count = 0
    for (var i in moves_form_change) {
        count ++
        var move = moves_form_change[i]
        push(count + ". " + move.names.zh + "\n")
    }
}

function machine() {
    if (moves_machine.length === 0) {
        push("")
        return
    }

    push("# 技能机: 序号. TM/TR — 技能\n")

    var count = 0
    for (var i in moves_machine) {
        var move = moves_machine[i]
        push((++count) + ". " + tooManyTmTr(move) + "\n")
    }
}

function tooManyTmTr(move) {
    var machines = move.learnMachines

    var hasTm = null
    var hasTr = null

    for (var k in machines) {
        if (machines[k].startsWith("tm")) {
            hasTm = machines[k]
        } else if  (machines[k].startsWith("tr")) {
            hasTr = machines[k]
        }
    }

    if (hasTr !== null) {
        return "TR"  + hasTr.replace("tr", "") + " - " + move.names.zh
    } else if (hasTm !== null) {
        return "TM" + hasTm.replace("tm", "") + " - " + move.names.zh
    } else return move.names.zh
}

reply()