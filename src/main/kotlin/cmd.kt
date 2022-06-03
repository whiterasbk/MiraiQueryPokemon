package bot.good

import kotlinx.serialization.json.put
import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.*
import java.lang.StringBuilder
import javax.script.ScriptException

object QueryCommand : SimpleCommand(
    QueryPokemon, Config.queryCmd, Config.queryzhCmd,
    description = Config.queryCmddesc
) {
    private val intRegex = Regex("\\d{1,10}")
    private val longRegex = Regex("\\d+")
    private val floatRegex = Regex("\\d+\\.\\d+")
    private val booleanRegex = Regex("(true|false)")
    private val forceToStringRegex = Regex("'\\S+'")

    private fun inferType(variable: String): Any {
        return if (forceToStringRegex.matches(variable))
            variable.substring(1, variable.lastIndex)
        else if (intRegex.matches(variable))
            variable.toInt()
        else if (longRegex.matches(variable))
            variable.toLong()
        else if (floatRegex.matches(variable))
            variable.toFloat()
        else if (booleanRegex.matches(variable))
            variable.toBoolean()
        else variable
    }

    @Handler
    suspend fun CommandSender.handle(vararg argument: String) {

        val quoteMessage = if (this is MemberCommandSenderOnMessage) QuoteReply(fromEvent.message) else EmptyMessageChain

        if (argument.isEmpty()) {
            sendMessage(quoteMessage + "查询指令未指定")
            return
        }

        val cmdName = argument[0]

        if (!QueryPokemon.queryExecutables.containsKey(cmdName) && cmdName != "pick") {
            sendMessage(quoteMessage + "查询指令 `$cmdName` 未找到")
            return
        }

        val lastArgument = argument.toMutableList()
        lastArgument.removeFirst()

        if (cmdName == "pick") {
            if (lastArgument.isEmpty()) {
                sendMessage(quoteMessage + "请选择序号")
                return
            }

            if (!lastArgument.first().canConvertToInt()) {
                sendMessage(quoteMessage + "请输入正确的格式")
                return
            }

            val number = lastArgument.first().toInt()

            if (passPickupDo.passData.isEmpty()) {
                sendMessage(quoteMessage + "还未执行查询操作")
                return
            }

            if (number <= passPickupDo.passData.size) {
                passPickupDo.action(number - 1)
            } else {
                sendMessage(quoteMessage + "序号超出界限, 最大: ${passPickupDo.passData.size}, 给定: $number")
            }

            return // it ends here
        }

        val queryExec = QueryPokemon.queryExecutables[cmdName]!!

//        if (lastArgument.isEmpty()) {
//            sendMessage("指令 `$cmdName` 的描述信息:\n" + queryExec.setupOptions["description"])
//            return
//        }



        // 传递过来的 graphql 部分的参数
        val passedQueryArguments = lastArgument// - scriptArgument

        if (lastArgument.size < queryExec.queryArguments.size) {
            // todo 采用默认参数
            sendMessage(quoteMessage + "参数个数不匹配: not enough parameters for query: `${queryExec.cmdName}`(require: ${queryExec.queryArguments.size}, provide: ${lastArgument.size})")
            return
        }

        // 传递过来的 script 部分的参数
        val scriptArgument = mutableListOf<String>() //lastArgument.filter { Regex("@[a-zA-Z_]\\d*=[^\\s]+").matches(it) }.toSet()
        if (lastArgument.size > queryExec.queryArguments.size) {
            // 先喂饱 queryExec.queryArguments, 剩下的 arg 才是 scriptArgument 的

        }

        if (queryExec.fuzzyQuery) {
            // 开启第一个参数 模糊查询
            multipleResult(
                queryIDResource(passedQueryArguments.first(), queryExec.fuzzyIn),
                Config.multiple_message_head,
                Config.multiple_message_empty,
                { (if (it.zhName.isEmpty()) it.enName else it.zhName) + ", id: " + it.id }) {

                val mpqa = passedQueryArguments.toMutableList()
                mpqa[0] = when (queryExec.fuzzyField) {
                    "en", "enName", "name" -> it.enName
                    "jp", "jpName", "nihon" -> it.jpName
                    "zh", "zh-cn", "cn", "local", "zhName" -> it.zhName
                    else -> it.id.toString()
                }

                query(queryExec, mpqa, scriptArgument)
            }
        } else {
            query(queryExec, passedQueryArguments, scriptArgument)
        }
    }


    suspend fun invoke(sender: CommandSender, group: Group, argument: String) {
        if (argument == "enable" || argument == "disable") return

        if (group.id in Config.sharpAllows) {
            sender.handle(*argument.split(" ").toTypedArray())
        } else {
            if (Config.sharp_deny.isNotBlank()) sender.sendMessage(Config.sharp_deny)
        }
    }

    private suspend fun CommandSender.query(query: QueryFile, argument: List<String>, scriptArgument: List<String>) {
        try {
            val atimeStamp = System.currentTimeMillis()

            if (query.setupOptions["auto-reload"] as Boolean) {
                query.reload()
                if (Config.debug) logger.info("cmd `${query.cmdName}` reloaded")
            }

            val invoked = query.execute(scriptArgument) { list ->
                list.forEachIndexed { index, pair ->
                    with(inferType(argument[index])) {
                        when (this) {
                            is Number -> put(pair.first, this)
                            is Boolean -> put(pair.first, this)
                            is String -> put(pair.first, this)
                            else -> put(pair.first, this.toString())
                        }
                    }
                }
            }
            val rendered = MessageChainBuilder()
            rendered += formatMessage(invoked, MessageContext(
                subject,
                if (this is CommandSenderOnMessage<*>) this.fromEvent else null,
                Config.err_picture_path
            ))
            val endtimeStamp = System.currentTimeMillis() - atimeStamp

            if (Config.getRuntime) {
                rendered += "\n执行时间: ${endtimeStamp.autoTimeUnit()}"
            }

            sendMessage(rendered.asMessageChain())
        } catch (e: ScriptException) {
            logger.error(e)
            if (Config.shouldScriptExceptionBeSent) sendMessage("渲染脚本执行错误: ${e.message}")
        } catch (e: Exception) {
            logger.error(e)
            if (Config.shouldOtherExceptionBeSent) {
                e.message?.let {
                    if (it.length <= 500) sendMessage("查询出错: $it") else sendMessage(PlainText("查询出错但是错误信息被我吞了") + Face(Face.WANG_WANG))
                }
            }
        }
    }

    /*============================================================================================*/

    private var passPickupDo: PickupDo<Any> = PickupDo(listOf()) {}

    private data class PickupDo <T: Any> (
        val passData: List<T>,
        val action: suspend (Int) -> Unit
    )

    private suspend fun <E: Any> multipleResult(result: List<E>, foundDo: suspend (E) -> Unit, emptyDo: suspend () -> Unit, multipleDo: suspend (List<E>) -> Unit) {

        passPickupDo = PickupDo(result) {
            val list = passPickupDo.passData
            foundDo(list[it] as E)
        }

        if (result.size == 1) {
            passPickupDo.action(0)
        } else if (result.isEmpty()) {
            emptyDo()
        } else {
            multipleDo(result)
        }
    }

    private suspend fun <E: Any> CommandSender.multipleResult(result: List<E>, multipleMessage: String, emptyMessage: String, elementField: (E) -> String = { it.toString() }, foundDo: suspend (E) -> Unit) {
        multipleResult(result, foundDo, {
            sendMessage(emptyMessage)
        }, {
            val builder = MessageChainBuilder()
            builder += multipleMessage + "\n"
            it.forEachIndexed { index, element  ->
                builder += "${index + 1}. ${ elementField(element) }\n"
            }
            builder += "使用/$primaryName pick <序号> 进行选择"
            sendMessage(builder.asMessageChain())
        })
    }
}

object BotCommand : CompositeCommand(
    QueryPokemon, Config.composite_cmd, Config.composite_zh_cmd,
    description = Config.composite_desc
) {

    @SubCommand
    @Description("列出当前可用的查询指令")
    suspend fun CommandSender.listCmd() {
        if (QueryPokemon.queryExecutables.isEmpty()) {
            sendMessage("没有任何查询指令被加载")
            return
        }

        val sb = StringBuilder("当前加载的查询指令: \n")
        var count = 0
        QueryPokemon.queryExecutables.forEach {
            sb.append("${ ++count }. `${it.key}`\n")
        }
        sendMessage(sb.toString().trim())
    }

    @SubCommand
    @Description("查看指令需要的参数信息")
    suspend fun CommandSender.argCmd(cmdName: String) {
        val sb = StringBuilder()
        QueryPokemon.queryExecutables.filterKeys { it == cmdName }.forEach { (_, u) ->
            if (u.queryArguments.isEmpty()) {
                sb.append("这个指令不需要参数\n")
            } else {
                sb.append("`${u.cmdName}`>参数简略表: \n")
                u.queryArguments.forEachIndexed { index, pair ->
                    sb.append("${index + 1}. 参数名称: ${pair.first}, 参数类型: ${pair.second}").append("\n")
                }
            }
            sb.append("描述信息:\n" + u.setupOptions["description"])
        }
        sendMessage(sb.toString().trim())
    }
}
