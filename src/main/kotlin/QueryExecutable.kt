package bot.good

import com.google.gson.JsonParser
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.synchronized
import me.sargunvohra.lib.pokekotlin.client.PokeApiClient
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.console.util.safeCast
import okhttp3.internal.toImmutableMap
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileFilter
import javax.script.Bindings
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import kotlin.properties.Delegates

class QueryFile(private val file: File, config: QueryExecutableConfiguration): QueryExecutable(file.readText(), config) {
    constructor(path: String, config: QueryExecutableConfiguration): this(File(path), config)

    fun reload() {
        initializeFragment(file.readText())
    }
}

class QueryExecutableConfiguration {
    lateinit var externalScriptFolder: File
    lateinit var externalGraphqlFolder: File
}

open class QueryExecutable(content: String, val config: QueryExecutableConfiguration) {

    private var templateString: String = ""
    private lateinit var setupString: String
    private var queryString: String = ""
    val setupOptions = mutableMapOf<String, Any>()
    var fuzzyQuery by Delegates.notNull<Boolean>()
    lateinit var fuzzyIn: String
    lateinit var fuzzyField: String
    lateinit var cmdName: String
    var operationName: String? = null
    val queryArguments = mutableListOf<Pair<String, String>>()
    private val templateJSCodes = mutableListOf<String>()

    private val templateJSInvokePlaceholder = mutableListOf<IntRange>()
    var argumentProcessCode: String = ""

    companion object {
        private val sharpSeparateRegex = Regex("#!\\w+")
        private val queryArgsRegex = Regex("query\\s\\w+\\((\\s*\\\$\\w+\\s*:\\s*\\w+\\s*,?\\s*)+\\)")
        private val scriptEngineManager = ScriptEngineManager()
        private val engine = scriptEngineManager.getEngineByName("JavaScript")
        private val sharedScriptList = mutableListOf<Any>()

        private val preLoadJsFolder = QueryPokemon.resolveDataFile("preload-script")
//        private val argumentProcessScriptFolder = MiraiBot.resolveDataFile("script")
        val fuzzyQueryDataFolder = QueryPokemon.resolveDataFile("fuzzy-query-data")
        private val translateMapperFolder = QueryPokemon.resolveDataFile("translate-mapper")
        private val translateMappers: MutableMap<String, MutableMap<String, String>> = mutableMapOf("main" to mutableMapOf())
        private lateinit var preloadCodes: String

        init {
            try {
                val sb = StringBuilder()

                preLoadJsFolder.listFiles(FileFilter {
                    it.extension == "js" && it.isFile
                })?.forEach {
                    sb.append(it.readText())
                    logger.info("[loaded script: ${it.name}]")
                }
                preloadCodes = sb.toString()

                // 初始化 翻译映射
                translateMapperFolder.listFiles(FileFilter {
                    (it.extension == "yml" || it.extension == "yaml") && it.isFile
                })?.forEach { file ->
                    translateMappers[file.nameWithoutExtension] = Yaml().load<Map<String, String>>(file.readText()).toMutableMap()
                    logger.info("[loaded mapper: ${file.name}]")
                }

                // 初始化 翻译映射脚本
                translateMapperFolder.listFiles(FileFilter { it.extension == "js" && it.isFile })?.forEach {
                    val binding = engine.createBindings()
                    binding["mappers"] = translateMappers
                    binding["dataFolder"] = QueryPokemon.dataFolder
                    engine.eval("var fuzzyQueryData = {}", binding)
                    fuzzyQueryDataFolder.listFiles { jf -> jf.isFile && jf.extension == "json" }?.map { jf ->
                        val code = "fuzzyQueryData[\"${jf.nameWithoutExtension}\"] = ${jf.readText()}"
                        engine.eval(code, binding)
                    }

                    engine.eval(it.reader(), binding)
                    logger.info("[loaded mapper script: ${it.name}]")
                }

            } catch (e: Exception) {
                logger.error("初始化配置失败")
                if (Config.strictMode) throw e else logger.error(e)
            }
        }

        private fun loadInitScript(eg: ScriptEngine, bindings: Bindings) {
            bindings["translator"] = Translate
            bindings["pokeapi"] = PokeApiClient()
            bindings["logger"] = logger
            bindings["localSpritesPath"] = Config.local_sprites_folder.let { if (it.isNotBlank()) it else null }
            bindings["dataFolder"] = QueryPokemon.dataFolder
            bindings["mappers"] = translateMappers.toImmutableMap()
            bindings["mapperIgnoreCase"] = { key: String, whichMap: String ->
                translateMappers[whichMap]?.filterKeys { it.equals(key, true) }?.values?.firstOrNull()
            }
            bindings["mainMapperIgnoreCase"] = { key: String ->
                translateMappers["main"]?.filterKeys { it.equals(key, true) }?.values?.firstOrNull()
            }

            bindings["readDataFileKt"] = { path: String ->
                QueryPokemon.resolveDataFile(path).readText()
            }

            bindings["readDataJSON"] = { path: String ->
                JSONObject(QueryPokemon.resolveDataFile(path).readText())
            }

            bindings["fileReader"] = { file: File ->
                file.reader()
            }

            bindings["jsonParser"] = JsonParser()

            // 加载 预置代码
            eg.eval(preloadCodes, bindings)
        }

        private fun scopedList(block: MutableList<Any>.(Bindings) -> String): String {

            val bindings = engine.createBindings()
            bindings["_outputArr"] = sharedScriptList
            engine.eval("""
                function push(obj) {
                    _outputArr.add(obj)
                }
            """, bindings)
            // todo 添加 groovy
            loadInitScript(engine, bindings)
            val result = sharedScriptList.block(bindings)
            engine.eval("delete _outputArr", bindings)
//            sharedScriptList.clear()
            return result
        }

        private val httpClient = HttpClient(OkHttp) {
            install(HttpTimeout)
        }

        suspend fun QueryExecutable.graphQuery(query: String, variables: JSONObject? = null): String {

            val rep = httpClient.post<HttpResponse> {
                if (!setupOptions.containsKey("url"))
                    url(Config.pokeapi_graphql_url)
                else {
                    url(setupOptions["url"] as String)
                    if (Config.debug) logger.info("using custom query url: ${setupOptions["url"]}")
                }

                headers {
                    append("content-type", "application/json")
                    append("accept", "*/*")
                }

                timeout {
                    connectTimeoutMillis = if (!setupOptions.containsKey("connectTimeout")) Config.connectTimout
                        else setupOptions["connectTimeout"] as Long

                    requestTimeoutMillis = if (!setupOptions.containsKey("requestTimeout")) Config.requestTimout
                        else setupOptions["requestTimeout"] as Long
                }

                body = buildJSONObject {
                    put("query", query)
                    variables?.let {
                        if (Config.debug) logger.info("passed arguments: $it")
                        put("variables", it)
                    }
                    operationName?.let { put("operationName", it) }
                }.toString()
            }

            val text = rep.readText()

            val rjs = JsonParser().parse(text).asJsonObject

            if (rjs.has("errors")) {
                val sb = StringBuilder("graphql执行出错: ")
                rjs["errors"].asJsonArray.forEach {
                    sb.append(it.asJsonObject["message"].asString).append("; ")
                }
                throw Exception(sb.toString())
            }

            return text
        }
    }

    init {
        initializeFragment(content)
    }

    protected fun initializeFragment(content: String) {

        templateString = ""
        queryString = ""
        argumentProcessCode = ""

        queryArguments.clear()
        setupOptions.clear()
        templateJSCodes.clear()
        templateJSInvokePlaceholder.clear()

        val first_split = content.split(sharpSeparateRegex)
        val split = first_split.subList(1, first_split.size)
        val pattern = sharpSeparateRegex.findAll(content)

        pattern.forEachIndexed { index, matchResult ->
            when(matchResult.value) {
                "#!setup" -> {
                    setupString = split[index].trim()

                    // 初始化设置
                    setupOptions += "fuzzy-query" to false
                    setupOptions += "auto-reload" to false
                    setupOptions += "description" to "not yet"
                    fuzzyField = "zh"
                    fuzzyIn = ""
                    operationName = null

                    Yaml().load<Map<String, Any>>(setupString).forEach {
                        setupOptions[it.key] = it.value
                    }

                    if (!setupOptions.containsKey("cmd")) throw Exception("at least give a cmd name!")
                    cmdName = setupOptions["cmd"] as String

                    if (setupOptions.containsKey("operationName")) operationName = setupOptions["operationName"].safeCast()

                    fuzzyQuery = setupOptions["fuzzy-query"] as Boolean
                    if (fuzzyQuery) {
                        fuzzyIn = setupOptions["fuzzy-in"] as String
                        fuzzyField = setupOptions["fuzzy-field"] as String
                    }

                    // 导入外部 js
                    if (setupOptions.containsKey("script-import")) {
                        val list = setupOptions["script-import"].safeCast<ArrayList<String>>()
                        list?.let { array ->
                            array.forEach {
                                val js = File(config.externalScriptFolder, it)
                                val code = js.readText().lines().filter { line -> !line.startsWith("import ") }.joinToString("\n")
                                templateString += "(: $code :)"
                            }
                        } ?: throw Exception("列表格式错误")
                    }

                    // 导入外部 查询文件
                    if (setupOptions.containsKey("graphql-import")) {
                        val list = setupOptions["graphql-import"].safeCast<ArrayList<String>>()
                        list?.let { array ->
                            array.forEach {
//                                val file = QueryPokemon.resolveDataFile("graphqls/$it")
                                val file = File(config.externalGraphqlFolder,it)
                                queryString += file.readText() + "\n"
                            }
                        } ?: throw Exception("列表格式错误")
                    }

                    // 解析外部文件参数
                    parseQueryArguments()

                    // 初始化参数注入脚本
                    if (setupOptions.containsKey("argument-inject")) {
                        val list = setupOptions["argument-inject"].safeCast<ArrayList<String>>()
                        list?.let { array ->
                            array.forEach {
//                                val file = QueryPokemon.resolveDataFile("scripts/$it")
                                val file = File(config.externalScriptFolder, it)
                                argumentProcessCode += file.readText() + "\n"
                            }
                        } ?: throw Exception("列表格式错误")
                    }
                }

                "#!template" -> {
                    templateString += split[index].trim()

                    if ("(:" in templateString && ":)" in templateString) {
                        templateString.onEachIndexed { index, char ->
                            if ((index != templateString.lastIndex - 1 && index != templateString.lastIndex) && char == '(' && templateString[index + 1] == ':') {
                                // start
                                val startIndex = index
                                val tsss = templateString.substring(startIndex, templateString.length)
                                var isClosed = false

                                tsss.onEachIndexed { tsss_index, t_char ->
                                    // end
                                    if (tsss_index != tsss.lastIndex && !isClosed && t_char == ':' && tsss[tsss_index + 1] == ')') {
                                        isClosed = true
                                        val rightWithBrackets = templateString.substring(startIndex, startIndex + tsss_index + 2)
                                        val code = rightWithBrackets
                                            .substring(2, rightWithBrackets.length)
                                            .substring(0, rightWithBrackets.length - 4)
                                        templateJSCodes += code
                                        templateJSInvokePlaceholder += startIndex .. startIndex + tsss_index + 2
                                    }
                                }
                            }
                        }
                    }
                }

                "#!graphql" -> {
                    queryString += split[index].trim()
                    parseQueryArguments()
                }
            }
        }

    }

    private fun parseQueryArguments() {
        val results = queryArgsRegex.findAll(queryString)
        results.forEach { result ->
            val leftBracketsInd = result.value.indexOf("(") + 1
            val substr = result.value.substring(leftBracketsInd until result.value.length)
                .replace(")", "")
                .replace(Regex("\\s+"), "")

            substr.split(",").forEach { argmod ->
                val mod = argmod.split(":")
                queryArguments += mod[0].replace("$", "") to mod[1]
            }
        }
    }

    suspend fun execute(buildArg: JSONObjectBuilder.(List<Pair<String, String>>) -> Unit): String {

        var pass: Any? = null
        val queryArg = buildJSONObject { buildArg(queryArguments) }.let {
            if (isUseInjection()) {
                val injected = injectArguments(it)
                pass = injected.second
                injected.first
            } else it
        }

        val data = graphQuery(queryString, queryArg)

        if (Config.debug) logger.info("response data: $data")

        return invokeScript(data, pass, queryArg)
    }

    private fun injectArguments(queryArg: JSONObject): Pair<JSONObject, Any?>{
        // 使用脚本注入参数后传递给 query 方法
        val aBindings = engine.createBindings()
        aBindings["input"] = queryArg
        loadInitScript(engine, aBindings)
        engine.eval(argumentProcessCode, aBindings)
        val out = aBindings["output"]
        val pass = aBindings["pass"]
        if (Config.debug) logger.info("injected: " + out.toString())
        if (out == null) throw Exception("inject script has to return a modified arguments list")
        if (out !is JSONObject) throw Exception("type input arguments cannot change")
        return out.cast<JSONObject>() to pass
    }

    private fun defineJsonData(name: String, data: String, bindings: Bindings) {
        engine.eval("var $name = $data", bindings)
    }

    private inline fun Bindings.putJson(name: String, data: String) = defineJsonData(name, data, this)

    /*
    * @params passing 上游的 js 传递的数据
    * */
    @OptIn(InternalCoroutinesApi::class)
    fun invokeScript(data: String = "{}", passing: Any? = null, extraArgument: Any? = null): String {
        // 定义资源要和执行脚本放在一起
        return synchronized(engine) {
            scopedList { bindings ->
                bindings["extraArgument"] = extraArgument
                bindings["pass"] = passing
                bindings.putJson("result", data)
                replaceInvokedScript {
                    val evalResult = engine.eval(it, bindings) ?: run {
                        val replacer = StringBuilder()
                        forEach { item -> replacer.append(item) }
                        replacer.toString()
                    }
                    clear()
                    evalResult
                }
            }
        }
    }

    private fun replaceInvokedScript(block: (String) -> Any): String {
        val resultList = mutableListOf<String>()
        templateJSCodes.forEach {
            resultList += block(it).toString()
        }
        return replaceHolder(resultList).trim()
    }

    private fun replaceHolder(replaceList: List<String>): String {

        if (replaceList.isEmpty()) return templateString

        val sb = StringBuilder()
        var placeholderIndex = 0
        var isVisited = false
        var imuCount = 0

        templateString.onEachIndexed { index, char ->

            if (index in templateJSInvokePlaceholder[placeholderIndex]) {
                if (!isVisited) {
                    isVisited = true
                    // 我也不知道我在干什么，但是模板渲染有问题，用 if 屎山解决算了
                    if (imuCount < replaceList.size) {
                        sb.append(replaceList[placeholderIndex])
                        imuCount ++
                    } else if (imuCount == replaceList.size) sb.append(char)
                }

                if (templateJSInvokePlaceholder[placeholderIndex].last - 1 == index) {
                    isVisited = false

                    if (placeholderIndex < templateJSInvokePlaceholder.lastIndex) placeholderIndex ++
                }
            } else {
                sb.append(char)
            }
        }

        return sb.toString()
    }

    fun isUseInjection(): Boolean {
        return setupOptions.containsKey("argument-inject")
    }
}