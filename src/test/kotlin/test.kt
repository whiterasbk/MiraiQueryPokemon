package bot.good

import me.sargunvohra.lib.pokekotlin.client.PokeApiClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun main(args: Array<String>) {

    val p = PokeApiClient().getPokemonForm(10319)

    println(p.sprites)
//    val f =  PokeApiClient().getBerry(1).flavors
//    f.forEach {
//        println(it.flavor)
//        println(it.potency)
//    }

//    val m = PokeApiClient().getPokemon(25).moves
//    m.forEach {
//        println(it.move)
//    }

//    val k = PokeApiClient().getPokemon(25).moves
//    println(k.size)
//    for (i in  k) {
//
//        println(i.move.name + ">>" + i.versionGroupDetails[0].versionGroup.name)
//    }

//    for (i in 1..12) {
//        val e = PokeApiClient().getPokemon(i).moves
//        println(e.size)
//        e.forEach {
//
//        }
//        Thread.sleep(1000)
//    }
//    val j = PokeApiClient().getPokemonList()
//    println(j)

//    var p = PokeApiClient().getItem(1176).name
//    println(p)




//    val b = JSONObject("{}")
//    val j = JSONObject(File("debug-sandbox/data/bot.good.QueryPokemon/cache-data/all-items.json").readText())
////    j.getJSONObject("data").getJSONArray("pokemon_v2_pokemon").forEach {
////        b.put(it.cast<JSONObject>().getInt("id").toString(), it.cast<JSONObject>().getString("name"))
////    }
////    b.put("1", 1)
////    b.put("1", listOf("1",13))
////    JSONArray()
////
////    println(b)
//
//    val o = JSONObject()

//
//    j.getJSONObject("data").getJSONArray("pokemon_v2_item").forEach {
//        val item = (it as JSONObject)
//        println(">>>>>>>>>>>>>>$item>>>>>>>>>>")
//        val id = item.getInt("id")
//        val names = item.getJSONArray("pokemon_v2_itemnames")
//
//        try {
//            val en = names.getJSONObject(0).getString("name")
//            val ja = names.getJSONObject(1).getString("name")
//            val zh = names.getJSONObject(2).getString("name")
//
//            o.put(id.toString(), buildJSONObject {
//                put("id", id)
//                put("enName", en.toString())
//                put("zhName", zh.toString())
//                put("jpName", ja.toString())
//            })
//        } catch (e:Exception ) {
//            b.put(id.toString(), names)
//            val en = names.getJSONObject(0).getString("name")
//            val zh = Translate.from(en)
//            Thread.sleep(500)
//            println("翻译: >>$zh")
//            o.put(id.toString(), buildJSONObject {
//                put("id", id)
//                put("enName", en.toString())
//                put("zhName", zh)
//                put("jpName", "")
//            })
//        }
//    }
//
//    println(b)
//    File("dssd.json").writeText(o.toString())



//    val k = PokeApiClient().getPokemonForm(10041).sprites.frontDefault
//    println(k)

}


//fun trans () {

//
//
//    var load = JSONObject(File("debug-sandbox/data/bot.good.QueryPokemon/cache-data/all-berry.json").readText())
//    var save = JSONObject()
//
//    load.getJSONObject("data").getJSONArray("berry").forEach {
//        val berry = it as JSONObject
//        val id = berry.getInt("id")
//        val names = berry.getJSONObject("item").getJSONArray("names")
//
//        println("names: ?$it")
//
//        try {
//            val first = names.getJSONObject(0).getString("name")
//            val second = names.getJSONObject(1).getString("name")
//            val third = names.getJSONObject(2).getString("name")
//
//            save.put("$id", buildJSONObject {
//                put("enName", first)
//                put("jpName", second)
//                put("zhName", third)
//            })
//
//        } catch (e: Exception ) {
//            val first = names.getJSONObject(0).getString("name")
//            val trans = Translate.from(first)
//            Thread.sleep(500)
//            println("=$trans")
//            save.put("$id", buildJSONObject {
//                put("enName", first)
//                put("zhName", trans)
//                put("jpName", "")
//            })
//        }
//
//
//    }
//
//    File("brr.json").writeText(save.toString())
//
//
//}

fun JSONArray.go(int: Int): JSONObject? = if (int < this.length()) this.getJSONObject(int) else null