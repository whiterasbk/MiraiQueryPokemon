package bot.good

import net.mamoe.mirai.console.command.*
import net.mamoe.mirai.console.util.cast
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.message.data.*
import javax.script.ScriptException

object QueryCommand : SimpleCommand(
    QueryPokemon, Config.queryCmd, Config.queryzhCmd,
    description = Config.queryCmddesc
) {
    private var nextCdTime = 0L
    private val pickupDos = mutableMapOf<Long, CmdPassPickupDo>()
    private val pickupDoForConsole: CmdPassPickupDo = CmdPassPickupDo.gen()

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
        limit (argument) {
            val cpd = if (this is MemberCommandSender) {
                if (pickupDos[group.id] == null) {
                    pickupDos[group.id] = CmdPassPickupDo()
                }

                pickupDos[group.id]!!
            } else {
                pickupDoForConsole
            }

            val quoteMessage = if (this is MemberCommandSenderOnMessage) QuoteReply(fromEvent.message) else EmptyMessageChain

            if (argument.isEmpty()) {
                sendMessage(quoteMessage + Config.unknownCmdHint)
                return@limit
            }

            val cmdName = argument[0]

            if (!QueryPokemon.queryExecutables.containsKey(cmdName) && cmdName != "pick") {
                sendMessage(quoteMessage + formatMessage(Config.unboundCmdHint , ctx = null, "cmd" to cmdName))
                return@limit
            }

            val lastArgument = argument.toMutableList()
            lastArgument.removeFirst()

            if (cmdName == "pick") {
                if (lastArgument.isEmpty()) {
                    sendMessage(quoteMessage + Config.pickupHint)
                    return@limit
                }

                if (!lastArgument.first().canConvertToInt()) {
                    sendMessage(quoteMessage + Config.wrongArgumentHint)
                    return@limit
                }

                val number = lastArgument.first().toInt()

                if (cpd.passPickupDo.passData.isEmpty()) {
                    sendMessage(quoteMessage + Config.unQueryHint)
                    return@limit
                }

                if (number <= cpd.passPickupDo.passData.size) {
                    cpd.passPickupDo.action(number - 1)
                } else {
                    sendMessage(quoteMessage + formatMessage(Config.pickNumberOutOfBoundsHint, ctx = null,
                        "max" to cpd.passPickupDo.passData.size,
                            "given" to number))
                }

                return@limit // it ends here
            }

            val queryExec = QueryPokemon.queryExecutables[cmdName]!!



//        if (lastArgument.isEmpty()) {
//            sendMessage("指令 `$cmdName` 的描述信息:\n" + queryExec.setupOptions["description"])
//            return
//        }



            // 传递过来的 graphql 部分的参数

            if (!queryExec.isUseInjection() && lastArgument.size < queryExec.queryArguments.size) {
                // todo 采用默认参数
                sendMessage(quoteMessage + "参数个数不匹配: not enough parameters for query: `${queryExec.cmdName}`(require: ${queryExec.queryArguments.size}, provide: ${lastArgument.size})")
                sendMessage(quoteMessage + formatMessage(Config.argumentsNotMatchHint, ctx = null,
                        "cmd" to queryExec.cmdName,
                            "require" to queryExec.queryArguments.size,
                            "provide" to lastArgument.size
                    ))
                return@limit
            }

            // 传递过来的 script 部分的参数
//        val scriptArgument = mutableListOf<String>() //lastArgument.filter { Regex("@[a-zA-Z_]\\d*=[^\\s]+").matches(it) }.toSet()
//        if (lastArgument.size > queryExec.queryArguments.size) {
//            // 先喂饱 queryExec.queryArguments, 剩下的 arg 才是 scriptArgument 的
//        }

            if (queryExec.fuzzyQuery) {
                // 开启第一个参数 模糊查询
                cpd.multipleResultx(
                    this,
                    queryIDResource(
                        lastArgument// - scriptArgument
                            .first(), queryExec.fuzzyIn
                    ),
                    (queryExec.setupOptions["when-fuzzy"] as? String) ?: Config.multiple_message_head,
                    (queryExec.setupOptions["fuzzy-empty"] as? String) ?: Config.multiple_message_empty,
                    { (if (it.zhName.isEmpty()) it.enName else it.zhName) + ", id: " + it.id }) {

                    val mpqa = lastArgument// - scriptArgument
                        .toMutableList()
                    mpqa[0] = when (queryExec.fuzzyField) {
                        "en", "enName", "name" -> it.enName
                        "jp", "jpName", "nihon" -> it.jpName
                        "zh", "zh-cn", "cn", "local", "zhName" -> it.zhName
                        else -> it.id.toString()
                    }

                    query(queryExec, mpqa)
                }
            } else {
                query(
                    queryExec, lastArgument
                )
            }
        }
    }

    private suspend fun CommandSender.limit(arg: Array<out String>, block: suspend () -> Unit) {
        if (this is MemberCommandSender) {
            if (!user.isOperator()) {

                // 单个命令权限
                if (Config.requirePermission && arg.isNotEmpty()) {
                    val cmdName = arg[0]
                    if (cmdName != "pick") {
                        if (QueryPokemon.queryExecutables.containsKey(cmdName)) {
                            Config.cmdPermitGroups[cmdName]?.let {
                                if (group.id !in it) {
                                    sendMessage(Config.currentGroupDenyHint)
                                    return
                                }
                            } ?: run {
                                sendMessage(Config.anyGroupDenyHint)
                                return
                            }
                        }
                    }
                }

                // 命令冷却
                if (Config.cd != 0L) {
                    val now = System.currentTimeMillis()
                    if (nextCdTime > now) {
                        sendMessage(formatMessage(Config.cdHint, ctx = null,"left" to (nextCdTime - now).autoTimeUnit()))
                        return
                    }
                }

                // 每人每天的限制
                if (Config.limit != 0) {
                    if (Data.limitation[user.id] == null) {
                        // first
                        Data.limitation[user.id] = mutableMapOf("times" to 1, "current" to System.currentTimeMillis())
                    } else {
                        val subj = Data.limitation[user.id]!!
                        subj["times"] = subj["times"].cast<Long>() + 1

                        if (subj["current"].cast<Long>() + Config.resetTime < System.currentTimeMillis()) {
                            // 这次调用过了至少 resetTime 以后
                            Data.limitation[user.id] = mutableMapOf("times" to 1, "current" to System.currentTimeMillis())
                        } else {
                            if (subj["times"].cast<Long>() > Config.limit) {
                                val msg = formatMessage(Config.limitHint, ctx = null,
                                    "rest" to (subj["current"].cast<Long>() + Config.resetTime - System.currentTimeMillis()).autoTimeUnit() )
                                sendMessage(msg)
                                return
                            }
                        }
                    }
                }

            }
        }

        block()

        if (this is MemberCommandSender) {
            if (!user.isOperator()) {
                if (Config.cd != 0L)
                    nextCdTime = System.currentTimeMillis() + Config.cd
            }
        }
    }

    suspend fun invoke(sender: CommandSender, group: Group, argument: String) {
        if (argument.startsWith("enable")  || argument.startsWith("disable")) return

        if (group.id in Config.sharpAllows) {
            sender.handle(*argument.split(" ").toTypedArray())
        } else {
            if (Config.sharp_deny.isNotBlank()) sender.sendMessage(Config.sharp_deny)
        }
    }

    private suspend fun CommandSender.query(query: QueryFile, argument: List<String>) {
        try {

            if (query.setupOptions["hint"] != null && (query.setupOptions["hint"] as String).isNotBlank()) {
                sendMessage(query.setupOptions["hint"] as String)
            }

            if (Config.starQueryingHint.isNotBlank()) sendMessage(Config.starQueryingHint)

            val atimeStamp = System.currentTimeMillis()

            if (query.setupOptions["auto-reload"] as Boolean) {
                query.reload()
                if (Config.debug) logger.info("cmd `${query.cmdName}` reloaded")
            }

            val invoked = query.execute { list ->
                argument.forEachIndexed { index, value ->
                    with(inferType(value)) {
                        if (index < list.size) {
                            when (this) {
                                is Number -> put(list[index].first, this)
                                is Boolean -> put(list[index].first, this)
                                is String -> put(list[index].first, this)
                                else -> put(list[index].first, this.toString())
                            }
                        } else {
                            put("$${index - list.size}", value)
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
}

class CmdPassPickupDo {

    companion object {
        fun gen(): CmdPassPickupDo {
            return CmdPassPickupDo()
        }
    }

    var passPickupDo: PickupDo<Any> = PickupDo(listOf()) {}

    data class PickupDo <T: Any> (
        val passData: List<T>,
        val action: suspend (Int) -> Unit
    )

    suspend fun <E: Any> multipleResult(
        result: List<E>,
        foundDo: suspend (E) -> Unit,
        emptyDo: suspend () -> Unit,
        multipleDo: suspend (List<E>) -> Unit) {

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

    suspend fun <E: Any> CommandSender.multipleResult(
        result: List<E>,
        multipleMessage: String,
        emptyMessage: String,
        elementField: (E) -> String = { it.toString() },
        foundDo: suspend (E) -> Unit) {
        multipleResult(result, foundDo, {
            sendMessage(emptyMessage)
        }, {
            val builder = MessageChainBuilder()
            builder += multipleMessage + "\n"
            it.forEachIndexed { index, element  ->
                builder += "${index + 1}. ${ elementField(element) }\n"
            }
            builder += "使用/${QueryCommand.primaryName} pick <序号> 进行选择"
            sendMessage(builder.asMessageChain())
        })
    }

    suspend fun <E: Any> multipleResultx(
        ctx: CommandSender,
        result: List<E>,
        multipleMessage: String,
        emptyMessage: String,
        elementField: (E) -> String = { it.toString() },
        foundDo: suspend (E) -> Unit) {
        multipleResult(result, foundDo, {
            ctx.sendMessage(emptyMessage)
        }, {
            val builder = MessageChainBuilder()
            builder += multipleMessage + "\n"
            it.forEachIndexed { index, element  ->
                builder += "${index + 1}. ${ elementField(element) }\n"
            }
            builder += "使用/${QueryCommand.primaryName} pick <序号> 进行选择"
            ctx.sendMessage(builder.asMessageChain())
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
