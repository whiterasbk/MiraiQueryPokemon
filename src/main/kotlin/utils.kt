package bot.good

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.contact.Contact.Companion.uploadImage
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.utils.MiraiLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.codec.digest.DigestUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.URL
import java.util.*
import java.util.concurrent.TimeUnit
import javax.script.Bindings
import javax.script.ScriptEngine
import kotlin.collections.List
import kotlin.collections.MutableMap
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

const val dollar = '$'
val intRegex = Regex("\\d{1,10}")
val longRegex = Regex("\\d+")
val floatRegex = Regex("\\d+\\.\\d+")
val booleanRegex = Regex("(true|false)")
val forceToStringRegex = Regex("'\\S+'")

private val iDResources = mutableMapOf<String, MutableMap<Int, IDResource>>()

val logger: MiraiLogger get() {
    return QueryPokemon.logger
}

//val inside: Boolean = true

fun queryIDResource(key: String, path: String): List<IDResource> {
    return loadIDResourceMapFromLocalCache(path).filter {
        key.isNotEmpty() && key.isNotBlank() &&
        (it.value.zhName.contains(key, true) ||
         it.value.enName.contains(key, true) ||
         it.value.jpName.contains(key, true) ||
         key.toIntOrNull() == it.key)
    }.map { it.value }
}

fun loadIDResourceMapFromLocalCache(name: String): MutableMap<Int, IDResource> {
    if (iDResources.containsKey(name)) {
        if (Config.debug) logger.info("load IDResource map from local cache")
        return iDResources[name]!!
    }

    val file = File(QueryExecutable.fuzzyQueryDataFolder, "$name.json")
    val map = mutableMapOf<Int, IDResource>()
    val json = JsonParser().parse(file.readText())

    json.asJsonObject.entrySet().forEach {
        map[it.key.toInt()] = IDResource(
            it.key.toInt(),
            it.value.asJsonObject.get("zhName").asString,
            it.value.asJsonObject.get("jpName").asString,
            it.value.asJsonObject.get("enName").asString
        )
    }

    iDResources[name] = map
    if (Config.debug) logger.info("load IDResource map from file")

    return map
}

object Translate {

    private val appid = Config.translate_appid
    private val salt = Config.translate_salt
    private val key = Config.translate_secret_key

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(Config.translate_connect_timeout, TimeUnit.MILLISECONDS)
        .readTimeout(Config.translate_read_timeout, TimeUnit.MILLISECONDS)
        .build()

    private val suspendHttpClient = HttpClient(OkHttp) {
        install(HttpTimeout)
    }

    fun from(text: String) = from(text, "en", 700)

    fun from(text: String, lang: String = "en", limit: Long = 700): String {

        return try {
            val paramBuilder = ParametersBuilder()
            paramBuilder.append("q", text)
            paramBuilder.append("from", lang)
            paramBuilder.append("to", "zh")
            paramBuilder.append("appid", appid)
            paramBuilder.append("salt", salt)
            paramBuilder.append("sign", DigestUtils.md5Hex(appid + text + salt + key))

            val requestUrl = Config.baidufanyi_http + "?" + paramBuilder.build().formUrlEncode()

//            if (Config.debug) {
//                if (!inside) logger.info("requesting: $requestUrl") else println("requesting: $requestUrl")
//            }

            if (Config.debug) logger.info("requesting: $requestUrl")

            val resp = httpClient.newCall(Request.Builder()
                .url(requestUrl)
                .get()
                .build()).execute()

            val respBody = resp.body?.string() ?: throw Exception()

            // 百度限制 1s 内只能调用一次
            Thread.sleep(limit)

            val rjs = JsonParser().parse(respBody).asJsonObject

            if (rjs.has("error_code")) {
                val code = rjs["error_code"].asInt
                val msg = rjs["error_msg"].asString


                logger.error("调用翻译接口异常($code), 已驳回: $msg")
//                if (!inside) {
//
//                } else println("调用翻译接口异常($code), 已驳回: $msg")

                text
            } else {
                val sb = StringBuilder()
                rjs["trans_result"].asJsonArray.forEach {
                    sb.append(it.asJsonObject["dst"].asString).append("\n")
                }
                sb.toString().trim()
            }
        } catch (e: Exception) {
//            if (!inside) {
                logger.error(e.message)
//            } else println(e.message)
            text
        }
    }

    suspend fun suspendFrom(text: String, lang: String = "en", limit: Long = 700): String {
        return try {
            val resp = suspendHttpClient.get<HttpResponse> {
                url {
                    protocol = URLProtocol.HTTP
                    host = Config.baidufanyi_http.replace("http://", "") // "api.fanyi.baidu.com/api/trans/vip/translate"

                    parameters.append("q", text)
                    parameters.append("from", lang)
                    parameters.append("to", "zh")
                    parameters.append("appid", appid)
                    parameters.append("salt", salt)
                    parameters.append("sign", DigestUtils.md5Hex(appid + text + salt + key))
                }

                timeout {
                    connectTimeoutMillis = Config.translate_connect_timeout
                    requestTimeoutMillis = Config.translate_read_timeout
                }
            }

            delay(limit)

            val rjs = JsonParser().parse(resp.readText()).asJsonObject

            if (rjs.has("error_code")) {
                val code = rjs["error_code"].asInt
                val msg = rjs["error_msg"].asString

                logger.error("调用翻译接口异常($code), 已驳回: $msg")

                text
            } else {
                val sb = StringBuilder()
                rjs["trans_result"].asJsonArray.forEach {
                    sb.append(it.asJsonObject["dst"].asString).append("\n")
                }
                sb.toString().trim()
            }
        } catch (e: Exception) {
            logger.error(e.message)
            text
        }
    }
}

fun String.canConvertToInt(): Boolean = Regex("\\d{1,10}").matches(this)

fun Long.autoTimeUnit(): String {
    return if (this < 1000) {
        "${this}ms"
    } else if (this in 1000..59999) {
        "${ String.format("%.2f", this.toFloat() / 1000) }s"
    } else {
        "${ this / 60_000 }m${ (this % 60_000) / 1000 }s"
    }
}

suspend fun InputStream.asMiraiImage(contact: Contact) : Image {
    return this.use { contact.uploadImage(it) }
}

private val imgHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(Config.img_connect_timeout, TimeUnit.MILLISECONDS)
        .readTimeout(Config.img_read_timeout, TimeUnit.MILLISECONDS)
        .build()
}

fun URL.openStreamOnClient() = imgHttpClient.newCall(
        Request.Builder()
            .url(this.toString())
            .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
            .header("content-type", "application/img")
            .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/101.0.4951.67 Safari/537.36")
            .let {
                if (this.toString().startsWith("https://cdn.jsdelivr.net")) {
                    logger.info("using jsdelivr for improving static resource access speed")
                    it.header("authority", "cdn.jsdelivr.net")
                } else it
            }
            .header("accept-encoding", "gzip, deflate, br")
            .header("scheme", "https")
            .header("sec-ch-ua-platform", "\"Windows\"")
            .header("accept-language", "zh-CN,zh;q=0.9")
            .get()
            .build()
).execute().body?.byteStream() ?: throw Exception("failed to load image resource")

fun ScriptEngine.kteval(script: String, bindings: Bindings? = null): Any? {
    val result: Any? = bindings?.let {
        eval(script, it)
    } ?: run {
        eval(script)
    }

    return if (result == null) null else result
}


fun buildJSONObject(builderAction: JSONObjectBuilder.() -> Unit): JSONObject {
    val builder = JSONObjectBuilder()
    builder.builderAction()
    return builder.json
}

fun buildJSONArray(builderAction: JSONArrayBuilder.() -> Unit): JSONArray {
    val builder = JSONArrayBuilder()
    builder.builderAction()
    return builder.arr
}

class JSONObjectBuilder {
    val json = JSONObject()
}

class JSONArrayBuilder {
    val arr = JSONArray()
}

inline fun JSONObjectBuilder.put(key: String, obj: JSONObject) = json.put(key, obj)
inline fun JSONObjectBuilder.put(key: String, arr: JSONArray) = json.put(key, arr)
inline fun JSONObjectBuilder.put(key: String, str: String) = json.put(key, str)
inline fun JSONObjectBuilder.put(key: String, int: Int) = json.put(key, int)
inline fun JSONObjectBuilder.put(key: String, bool: Boolean) = json.put(key, bool)
inline fun JSONObjectBuilder.put(key: String, double: Double) = json.put(key, double)
inline fun JSONObjectBuilder.put(key: String, long: Long) = json.put(key, long)
inline fun JSONObjectBuilder.put(key: String, number: Number) = json.put(key, number)
inline fun JSONObjectBuilder.put(key: String, list: List<*>) = json.put(key, list)
inline fun JSONObjectBuilder.put(key: String, map: Map<*, *>) = json.put(key, map)

inline fun JSONArrayBuilder.add(obj: JSONObject) = arr.put(obj)
inline fun JSONArrayBuilder.add(arr: JSONArray) = arr.put(arr)
inline fun JSONArrayBuilder.add(str: String) = arr.put(str)
inline fun JSONArrayBuilder.add(int: Int) = arr.put(int)
inline fun JSONArrayBuilder.add(bool: Boolean) = arr.put(bool)
inline fun JSONArrayBuilder.add(double: Double) = arr.put(double)
inline fun JSONArrayBuilder.add(long: Long) = arr.put(long)
inline fun JSONArrayBuilder.add(number: Number) = arr.put(number)
inline fun JSONArrayBuilder.add(list: List<*>) = arr.put(list)
inline fun JSONArrayBuilder.add(map: Map<*, *>) = arr.put(map)


inline val JsonElement.o get() = this.asJsonObject
inline val JsonElement.a get() = this.asJsonArray
inline val JsonElement.s get() = this.asString
inline val JsonElement.i get() = this.asInt
inline val JsonElement.b get() = this.asBoolean
inline val JsonElement.l get() = this.asLong
inline val JsonElement.d get() = this.asDouble
inline val JsonElement.n get() = this.asJsonNull

