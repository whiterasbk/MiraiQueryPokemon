package bot.good

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandSender.Companion.asMemberCommandSender
import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.AutoSavePluginData
import net.mamoe.mirai.console.data.ValueDescription
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.id
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.info
import java.io.File
import java.io.FileFilter

object QueryPokemon : KotlinPlugin(
    JvmPluginDescription(
        id = "bot.good.QueryPokemon",
        version = "0.0.1",
    )
) {

    val queryExecutables = mutableMapOf<String, QueryFile>()

    override fun onEnable() {
        logger.info { "query pokemon loaded" }
        Config.reload()
        Data.reload()

        CommandManager.registerCommand(BotCommand)
        CommandManager.registerCommand(QueryCommand)

        resolveDataFile("query").listFiles(FileFilter {
            it.extension == "query" || it.isDirectory
        })?.forEach {
            try {
                val cfg = QueryExecutableConfiguration()
                if (it.isFile) {
                    cfg.externalGraphqlFolder = resolveDataFile("graphqls")
                    cfg.externalScriptFolder = resolveDataFile("scripts")

                    val file = QueryFile(it, cfg)
                    queryExecutables[file.cmdName] = file
                    logger.info("[loaded cmd: `${file.cmdName}`]")
                } else if (it.isDirectory) {
                    val queryFile = File(it, "main.query")
                    if (!queryFile.exists()) throw Exception("no main.query exists!")

                    cfg.externalGraphqlFolder = it
                    cfg.externalScriptFolder = it

                    val file = QueryFile(queryFile, cfg)
                    queryExecutables[file.cmdName] = file
                    logger.info("[loaded cmd: `${file.cmdName}`]")
                }
            } catch (e: Exception) {
                logger.error("加载 ${if (it.isDirectory) "目录" else "文件"}: `${it.name}` 失败")
                logger.error(e)
            }
        }

        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            if (message.content.startsWith(Config.simply_cmd_trigger_char)) {
                val msg = message.content.slice(Config.simply_cmd_trigger_char.length until message.content.length)
                QueryCommand.invoke(sender.asMemberCommandSender(), group, msg)
            }
        }

        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            if (message.content.startsWith(Config.simply_cmd_trigger_char)) {
                val msg = message.content.slice(Config.simply_cmd_trigger_char.length until message.content.length).split(" ")

                if (msg.size == 1) {
                    if (msg[0] == "enable") {
                        if (sender.isOperator()) {
                            CommandManager.executeCommand(sender.asMemberCommandSender(), PlainText("/permission permit m${group.id}.* ${QueryPokemon.id}:*"), false)
                            Config.sharpAllows += group.id
                            group.sendMessage(Config.enablePluginHint)
                        } else group.sendMessage(Config.requireOperatorHint)
                    }

                    if (msg[0] == "disable") {
                        if (sender.isOperator()) {
                            CommandManager.executeCommand(sender.asMemberCommandSender(), PlainText("/permission cancel m${group.id}.* ${QueryPokemon.id}:*"), false)
                            Config.sharpAllows -= group.id
                            group.sendMessage(Config.disablePluginHint)
                        } else group.sendMessage(Config.requireOperatorHint)
                    }
                } else if (msg.size == 3) {
                    // 设置 命令权限
                    when (msg[0]) {
                        "enable" -> {
                            val cmd = msg[1]
                            val toGroup = msg[2]

                            if (sender.isOperator()) {
                                if (cmd == "all") {
                                    val tg = if (toGroup == "this") group.id
                                        else if (toGroup.matches(intRegex)) toGroup.toLong() else throw Exception(Config.groupNumberWrongFormatHint)

                                    for (i in queryExecutables.keys) {
                                        if (Config.cmdPermitGroups[i] == null) {
                                            Config.cmdPermitGroups[i] = mutableListOf(tg)
                                        } else {
                                            Config.cmdPermitGroups[i]!! += tg
                                        }
                                    }

                                    group.sendMessage(Config.enableCmdSuccessHint)
                                } else {
                                    if (cmd !in queryExecutables.keys) {
                                        group.sendMessage(Config.noSuchCmdHint)
                                        return@subscribeAlways
                                    }

                                    if (toGroup == "this") {
                                        if (Config.cmdPermitGroups[cmd] == null) {
                                            Config.cmdPermitGroups[cmd] = mutableListOf(group.id)
                                        } else {
                                            Config.cmdPermitGroups[cmd]!! += group.id
                                        }
                                        group.sendMessage(Config.enableCmdSuccessHint)
                                    } else if (toGroup.matches(longRegex)) {
                                        if (Config.cmdPermitGroups[cmd] == null) {
                                            Config.cmdPermitGroups[cmd] = mutableListOf(toGroup.toLong())
                                        } else {
                                            Config.cmdPermitGroups[cmd]!! += toGroup.toLong()
                                        }
                                        group.sendMessage(Config.enableCmdSuccessHint)
                                    } else {
                                        subject.sendMessage(Config.groupNumberWrongFormatHint)
                                    }
                                }
                            } else group.sendMessage(Config.requireOperatorHint)
                        }

                        "disable" -> {
                            val cmd = msg[1]
                            val toGroup = msg[2]

                            if (sender.isOperator()) {
                                if (cmd == "all") {
                                    val tg = if (toGroup == "this") group.id
                                    else if (toGroup.matches(intRegex)) toGroup.toLong() else throw Exception(Config.groupNumberWrongFormatHint)

                                    for (i in queryExecutables.keys) {
                                        Config.cmdPermitGroups[i]?.let {
                                            val result = it.remove(tg)
                                            if (!result) group.sendMessage("$i: ${Config.disableCmdFailureHint}")
                                        }
                                    }

                                    group.sendMessage(Config.disableCmdSuccessHint)
                                } else {
                                    if (cmd !in queryExecutables.keys) {
                                        group.sendMessage(Config.noSuchCmdHint)
                                        return@subscribeAlways
                                    }

                                    if (toGroup == "this") {
                                        Config.cmdPermitGroups[cmd]?.let {
                                            val result = it.remove(group.id)
                                            group.sendMessage(if (result) Config.disableCmdSuccessHint else Config.disableCmdFailureHint)
                                        }
                                    } else if (toGroup.matches(longRegex)) {
                                        Config.cmdPermitGroups[cmd]?.let {
                                            val result = it.remove(toGroup.toLong())
                                            group.sendMessage(if (result) Config.disableCmdSuccessHint else Config.disableCmdFailureHint)
                                        }
                                    } else {
                                        subject.sendMessage(Config.groupNumberWrongFormatHint)
                                    }
                                }
                            } else group.sendMessage(Config.requireOperatorHint)
                        }
                    }
                }
            }
        }
    }
}

object Config : AutoSavePluginConfig("config") {
    @ValueDescription("当开始查询的时候的提示")
    val starQueryingHint: String by value("")
    @ValueDescription("当指令发送者没有该指令的权限的时候的提示")
    val anyGroupDenyHint: String by value("任何群都不能使用这个命令")
    @ValueDescription("当指令发送者没有该指令的权限的时候的提示")
    val currentGroupDenyHint: String by value("此群没有该命令的权限")
    @ValueDescription("当使用pick命令时, 序号超过范围的提示")
    val pickNumberOutOfBoundsHint: String by value("序号超出界限, 最大: `max`, 给定: `given`")
    @ValueDescription("当指令收到的参数与在.graphql里定义的参数个数不一致时候的提示")
    val argumentsNotMatchHint: String by value( "参数个数不匹配: not enough parameters for query: `cmd`(require: `require`, provide: `provide`)")
    @ValueDescription("当未执行查询操作就使用pick命令的时候的提示")
    val unQueryHint: String by value("还未执行查询操作")
    @ValueDescription("当指令接受的参数格式不正确的时候的提示")
    val wrongArgumentHint: String by value("请输入正确的格式")
    @ValueDescription("使用pick指令时, 对于模糊查询启动时候的提示")
    val pickupHint: String by value("请选择序号")
    @ValueDescription("当查询指令不存在时候的提示")
    val unboundCmdHint: String by value("查询指令 `cmd` 未找到")
    @ValueDescription("命令发送者未指定查询指令的时候的提示")
    val unknownCmdHint: String by value("查询指令未指定")
    @ValueDescription("当使用enable/disable指令时操作者不是管理员及以上时的提示")
    val requireOperatorHint by value("要管理员以上才能操作")
    @ValueDescription("当使用enable/disable指令时输入群名格式不正确时的提示")
    val groupNumberWrongFormatHint by value("请输入正确的群号")
    @ValueDescription("当使用enable/disable指令时没有找到给定查询指令时的提示")
    val noSuchCmdHint by value("没有该命令")
    @ValueDescription("当使用enable指令时操作成功的提示")
    val enableCmdSuccessHint by value("添加成功")
    @ValueDescription("当使用enable指令时操作失败的提示")
    val enableCmdFailureHint by value("添加失败")
    @ValueDescription("当使用disable指令时操作成功的提示")
    val disableCmdSuccessHint by value("移除成功")
    @ValueDescription("当使用disable指令时操作失败的提示")
    val disableCmdFailureHint by value("移除失败")
    @ValueDescription("当使用enable指令开启插件功能操作成功的提示")
    val enablePluginHint by value("已在本群开启插件")
    @ValueDescription("当使用disable指令关闭插件功能操作成功的提示")
    val disablePluginHint by value("已在本群关闭插件")
    @ValueDescription("使用指令是否需要权限")
    val requirePermission: Boolean by value(true)
    @ValueDescription("每个指令可以使用的群")
    val cmdPermitGroups: MutableMap<String, MutableList<Long>> by value()
    @ValueDescription("当超过查询次数限制时的提示")
    val limitHint: String by value("次数超过限制, 还剩 `rest` 到重置时间")
    @ValueDescription("查询次数重置时间间隔, 以毫秒为单位, 默认是 24 * 60 * 60 * 1000L 也就是一天")
    val resetTime: Long by value(24 * 60 * 60 * 1000L)
    @ValueDescription("每人每resetTime的限制次数")
    val limit: Int by value(10)
    @ValueDescription("超过 每人每resetTime的限制次数时的提示")
    val cdHint: String by value("太短了!不行! 还剩: `left`")
    @ValueDescription("查询指令冷却时间, 为 0 则没有冷却时间")
    val cd: Long by value(0L)
    @ValueDescription("本地图库, 如果不为空则会优先从这个路径加载图片")
    val local_sprites_folder by value("")
    @ValueDescription("当获取在线图片失败时候的替换图片")
    val err_picture_path by value("")
    @ValueDescription("获取在线图片的连接超时")
    val img_connect_timeout by value(3000L)
    @ValueDescription("读取取在线图片的连接超时")
    val img_read_timeout by value(3000L)
    @ValueDescription("enable/disable指令的前缀触发词")
    val simply_cmd_trigger_char by value("#")
    @ValueDescription("当触发多结果时候的提示")
    val multiple_message_head by value("查询到多个结果")
    @ValueDescription("当模糊查询没有找到任何的提示")
    val multiple_message_empty by value("没有找的任何结果")
    @ValueDescription("查询连接超时")
    val requestTimout by value(60 * 1000L)
    @ValueDescription("查询读取超时")
    val connectTimout by value(60 * 1000L)
    @ValueDescription("宝可梦 graphql 的 api 网址")
    val pokeapi_graphql_url by value("https://beta.pokeapi.co/graphql/v1beta")
    @ValueDescription("是否启用严格模式, 若填 true 则只要一个query文件加载失败则之后的query文件都将无法加载")
    val strictMode by value(false)
    @ValueDescription("翻译读取超时")
    val translate_read_timeout by value(30_000L)
    @ValueDescription("翻译连接超时")
    val translate_connect_timeout by value(30_000L)
    @ValueDescription("百度翻译网址")
    val baidufanyi_http: String by value("http://api.fanyi.baidu.com/api/trans/vip/translate")
    @ValueDescription("百度翻译密钥")
    val translate_secret_key: String by value("")
    @ValueDescription("百度翻译 盐 可以时随机字符")
    val translate_salt: String by value("")
    @ValueDescription("百度翻译 appid")
    val translate_appid: String by value("")
    @ValueDescription("是否开启调试模式")
    val debug by value(false)
    @ValueDescription("当渲染脚本出现错误时是否上报到聊天环境")
    val shouldScriptExceptionBeSent by value(true)
    @ValueDescription("当渲染出现其他错误时是否上报到聊天环境")
    val shouldOtherExceptionBeSent by value(true)
    @ValueDescription("是否在每条结果上加上执行时间")
    val getRuntime by value(true)
    @ValueDescription("查询指令的触发前缀")
    val queryCmd by value("query")
    @ValueDescription("查询指令的中文别名")
    val queryzhCmd by value("查询")
    @ValueDescription("帮助指令的触发前缀")
    val composite_cmd by value("pokemon")
    @ValueDescription("帮助指令的中文别名")
    val composite_zh_cmd by value("宝可梦")
    @ValueDescription("查询指令的描述")
    val queryCmddesc by value("查宝可梦")
    @ValueDescription("帮助指令的描述")
    val composite_desc by value("pokemon bot 指令命名空间")
    @ValueDescription("启用插件的群号")
    val sharpAllows: MutableSet<Long> by value(mutableSetOf())
    @ValueDescription("当未开启插件时候的提示")
    val sharp_deny by value("你甚至没有权限")
}

object Data : AutoSavePluginData("data") {
    val limitation: MutableMap<Long, MutableMap<String, Long>> by value()
}