package bot.good

import net.mamoe.mirai.console.command.CommandManager
import net.mamoe.mirai.console.command.CommandSender.Companion.asMemberCommandSender
import net.mamoe.mirai.console.data.AutoSavePluginConfig
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
import java.io.FileFilter

object QueryPokemon : KotlinPlugin(
    JvmPluginDescription(
        id = "bot.good.QueryPokemon",
        version = "0.0.1",
    )
) {

    val queryExecutables = mutableMapOf<String, QueryFile>()

    override fun onEnable() {
        logger.info { "Pokeapi loaded" }
        Config.reload()

        logger.info(Config.composite_zh_cmd)

        CommandManager.registerCommand(BotCommand)
        CommandManager.registerCommand(QueryCommand)

        resolveDataFile("query").listFiles(FileFilter {
            it.isFile && it.extension == "query"
        })?.forEach {
            val file = QueryFile(it)
            queryExecutables[file.cmdName] = file
            logger.info("[loaded cmd: `${file.cmdName}`]")
        }

        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            if (message.content.startsWith(Config.simply_cmd_trigger_char)) {
                val msg = message.content.slice(Config.simply_cmd_trigger_char.length until message.content.length)
                QueryCommand.invoke(sender.asMemberCommandSender(), group, msg)
            }
        }

        globalEventChannel().subscribeAlways<GroupMessageEvent> {
            if (message.content.startsWith(Config.simply_cmd_trigger_char)) {
                val msg = message.content.slice(Config.simply_cmd_trigger_char.length until message.content.length)

                if (msg == "enable") {
                    if (sender.isOperator()) {
                        CommandManager.executeCommand(sender.asMemberCommandSender(), PlainText("/permission permit m${group.id}.* ${QueryPokemon.id}:*"), false)
                        Config.sharpAllows += group.id
                    } else group.sendMessage("要管理员以上才能操作")
                }

                if (msg == "disable") {
                    if (sender.isOperator()) {
                        CommandManager.executeCommand(sender.asMemberCommandSender(), PlainText("/permission cancel m${group.id}.* ${QueryPokemon.id}:*"), false)
                        Config.sharpAllows -= group.id
                    } else group.sendMessage("要管理员以上才能操作")
                }
            }
        }
    }
}

object Config : AutoSavePluginConfig("config") {
    val local_sprites_folder by value("")
    val err_picture_path by value("")
    val img_connect_timeout by value(3000L)
    val img_read_timeout by value(3000L)
    val simply_cmd_trigger_char by value("#")
    val multiple_message_head by value("查询到多个结果")
    val multiple_message_empty by value("没有找的任何结果")
    val requestTimout by value(60 * 1000L)
    val connectTimout by value(60 * 1000L)
    val pokeapi_graphql_url by value("https://beta.pokeapi.co/graphql/v1beta")
    val strictMode by value(false)
    val translate_read_timeout by value(30_000L)
    val translate_connect_timeout by value(30_000L)
    val baidufanyi_http: String by value("http://api.fanyi.baidu.com/api/trans/vip/translate")
    val translate_secret_key: String by value()
    val translate_salt: String by value()
    val translate_appid: String by value()
    val debug by value(true)
    val shouldScriptExceptionBeSent by value(true)
    val shouldOtherExceptionBeSent by value(true)
    val getRuntime by value(true)
    val queryCmd by value("query")
    val queryzhCmd by value("查")
    val composite_cmd by value("pokemon")
    val composite_zh_cmd by value("宝可梦")
    val queryCmddesc by value("查宝可梦")
    val composite_desc by value("pokemonbot指令命名空间")
    val sharpAllows: MutableSet<Long> by value()
    val sharp_deny by value("你甚至没有权限")
    // val wiki_edit_url by value("https://wiki.52poke.com/index.php?title=%E5%AE%9D%E5%8F%AF%E6%A2%A6%E5%88%97%E8%A1%A8%EF%BC%88%E6%8C%89%E5%85%A8%E5%9B%BD%E5%9B%BE%E9%89%B4%E7%BC%96%E5%8F%B7%EF%BC%89/%E7%AE%80%E5%8D%95%E7%89%88&action=edit")
    // val wiki_edit_url_regex_pattern by value("\\d{3}\\|[\\uff31\\uff3a\\u30fb\\uff1a\\u2640\\u2642\\u4e00-\\u9fa5\\u2160-\\u2169A-Za-z]+\\|[\\uff31\\uff3a\\uff1a\\u2640\\u2642\\u30A0-\\u30FFA-Za-z\\s\\uff12]+\\|[\\u2019\\u2640\\u2642A-Za-zÀ-ÿ:\\s\\d-'\\.]+")
}