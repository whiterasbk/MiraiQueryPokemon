package bot.good

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import java.io.File
import java.io.FileNotFoundException
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.createType
import kotlin.reflect.full.declaredMemberProperties

private var faceIdListInstance: MutableMap<String, Int>? = null
val faceIdList: MutableMap<String, Int> get() {
    if (faceIdListInstance != null) return faceIdListInstance!!
    val list = mutableMapOf<String, Int>()
    val clz: KClass<Face> = Face::class
    val comp: KClass<Face.Companion>? = clz.companionObject as KClass<Face.Companion>?
    val intType = (Int::class).createType()
    comp?.declaredMemberProperties?.forEach {
        it as KProperty1<Face.Companion, Int>
        if (it.returnType == intType) {
            list[it.name] = it.get(Face.Companion)
        }
    }
    faceIdListInstance = list
    return list
}

suspend fun MessageEvent.formatSendMessage(formatter: String, vararg args: Pair<String, Message>) {
    val message = formatMessage(formatter, MessageContext(subject, this), pair = args)
    subject.sendMessage(message)
}

@JvmName("formatSendMessageS2S")
suspend fun MessageEvent.formatSendMessage(formatter: String, vararg args: Pair<String, String>) {
    val message = formatMessage(formatter, MessageContext(subject, this), pair =  args)
    subject.sendMessage(message)
}

@JvmName("formatSendMessageS2A")
suspend fun MessageEvent.formatSendMessage(formatter: String, vararg args: Pair<String, Any>) {
    val message = formatMessage(formatter, MessageContext(subject, this), pair = args)
    subject.sendMessage(message)
}

@JvmName("sendFormatMessage")
suspend fun CommandSender.sendFormatMessage(formatter: String, vararg args: Pair<String, Any?>) {
    val message = formatMessage(formatter, pair = args)
    sendMessage(message)
}

fun formatFace(input: String): MessageChain {
    if ('[' !in input || ']' !in input) return EmptyMessageChain + PlainText(input)

    val regex = Regex("\\[[\\u4e00-\\u9fa5_a-zA-Z0-9]+\\]")
    val messageChain = MessageChainBuilder()
    val strSplit = input.split(regex)
    val faceNames = mutableListOf<String>()
    regex.findAll(input).iterator().forEach { faceNames += it.value }

    for (i in 0 until strSplit.size - 1) {
        val faceName = faceNames[i].replace("[", "").replace("]", "")
        messageChain += if (faceName in faceIdList.keys) {
            PlainText(strSplit[i]) + Face(faceIdList[faceName]!!)
        } else {
            PlainText(strSplit[i] + "[$faceName]")
        }
    }

    messageChain += strSplit[strSplit.lastIndex]
    return messageChain.asMessageChain()
}

suspend fun formatMessage(formatter: String, map: Map<String, Any?>? = null, msgCtx: MessageContext? = null): MessageChain {

    if (formatter.filter { it == '`' }.length % 2 != 0) return formatFace(formatter)

    val regex = Regex("`(([\\w .:]+)|(:\\w+ (([\\w.,;!&@=%#*^\$~\\-+()]+)|(https?|ftp|file)://[-A-Za-z0-9+&@#/%?=~_|!:,.;\\u4e00-\\u9fa5]+[-A-Za-z0-9+&@#/%=~_|\\u4e00-\\u9fa5])))`")
    val messageChain = MessageChainBuilder()
    val improcMsgr = formatter.split(regex).map { formatFace(it) }
    val replacers = mutableListOf<String>()
    regex.findAll(formatter).iterator().forEach { replacers += it.value }
    val mediaMessage = mutableListOf<Message>()

    val current = LocalDateTime.now()
    val finalMap = map?.toMutableMap() ?: mutableMapOf()
    finalMap["sys.hour"] = PlainText(current.hour.toString())
    finalMap["sys.minute"] = PlainText(current.minute.toString())
    finalMap["sys.second"] = PlainText(current.second.toString())

    finalMap["sys.year"] = PlainText(current.year.toString())
    finalMap["sys.month"] = PlainText(current.month.toString())
    finalMap["sys.day"] = PlainText(current.dayOfMonth.toString())
    finalMap["sys.week"] = PlainText(current.dayOfWeek.toString().lowercase())

    finalMap["sys.time"] = PlainText(current.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")))
    finalMap["sys.date"] = PlainText(current.toLocalDate().toString())
    finalMap["atAll"] = AtAll

    try {
        for (i in 0 until improcMsgr.size - 1) {
            val replacer = replacers[i].replace("`", "")
            messageChain += if (finalMap.containsKey(replacer)) {
                val obj = finalMap[replacer]
                if (obj == null) {
                    improcMsgr[i]
                } else if (obj is MessageChain) {
                    improcMsgr[i] + obj
                } else if (obj is Message) {
                    val message: Message = obj
                    if (message !is PlainText && message !is Face && message !is Image && message !is At && message !is AtAll && message !is QuoteReply) {
                        mediaMessage += message
                        EmptyMessageChain
                    } else improcMsgr[i] + message
                } else if (obj is String || obj is Number || obj is Boolean) {
                    improcMsgr[i] + PlainText(obj.toString())
                } else improcMsgr[i] + "`!$replacer`"
            } else  {
                if (replacer.startsWith(":")) {
                    // 以 `:` 开头的指令, 但是 finalMap 里面没有
                    val args = replacer.substring(1 until replacer.length).split(" ")
                    val arg = if (args.size > 1) args[1] else ""

                    val result: Message = msgCtx?.let { // todo 替换自定义的 pack
                        when (args[0]) {
                            "img" -> {
                                if (arg.startsWith("file://")) {
                                    try {
                                        it.contact?.let { contact ->
                                            File(arg.replace("file://", "")).inputStream().asMiraiImage(contact)
                                        } ?: PlainText("`假装上传了一张图`")
                                    } catch (e: Exception) {
                                        logger.error(e)
                                        PlainText("[!你看到了一张本地图片]")
                                    }
                                } else if (arg.startsWith("http://") or arg.startsWith("https://") or arg.startsWith("ftp://")) {
                                    try {
                                        it.contact?.let { contact ->
                                            URL(arg).openStreamOnClient().asMiraiImage(contact)
                                        } ?: PlainText("`假装上传了一张网图`")
                                    } catch (e: Exception) {
                                        logger.error("accessing url: $arg")
                                        logger.error(e)
                                        try {
                                            if (it.errorImageReplacePath != null && it.errorImageReplacePath.isNotBlank())
                                                it.contact?.let { contact ->
                                                    File(it.errorImageReplacePath).inputStream().asMiraiImage(contact)
                                                } ?: PlainText("`你看见了一张图片`")
                                            else PlainText("[!你看到了一张图片]")
                                        } catch (ie: Exception) {
                                            logger.error("也许是文件找不到了, 总之检查一下 err_picture_path 比较好")
                                            logger.error(ie)
                                            PlainText("[!你看到了一张图片]")
                                        }
                                    }
                                } else throw Exception("operation `img` doesn't support such protocol: $arg")
                            }

                            "at" -> {
                                if (Regex("\\d+").matches(arg)) {
                                    At(arg.toLong())
                                } else if (arg == "sender") {
                                    it.event?.sender?.id?.let { id ->
                                        At(id)
                                    } ?: PlainText("`此处@了一个人`")
                                } else throw Exception("operation `at` doesn't support such argument: $arg")
                            }

                            "reply" -> it.event?.let { event -> QuoteReply(event.message) } ?: PlainText("" /*"`假装回复`"*/)

                            "repeat" -> it.event?.message ?: PlainText("`假装复读`")

                            "senderName" -> PlainText(it.event?.senderName ?: "`!senderName`" )

                            "senderNick" -> PlainText(it.event?.sender?.nick ?: "`!senderNick`")

                            "senderRemark" -> PlainText(it.event?.sender?.remark ?: "`!senderRemark`")

                            "groupName" -> PlainText(if (it.event != null && it.event is GroupMessageEvent) it.event.group.name else "`!groupName`" )

                            "senderNameCard" -> PlainText(if (it.event != null && it.event is GroupMessageEvent) it.event.sender.nameCard else "`!senderNameCard`" )

                            "senderSpecialTitle" -> PlainText(if (it.event != null && it.event is GroupMessageEvent) it.event.sender.specialTitle else "`!senderSpecialTitle`" )

                            else -> PlainText("`!$replacer`")
                        }
                    } ?: throw Exception("argument `MessageContext` is required")
                    improcMsgr[i] + result
                } else improcMsgr[i] + "`!$replacer`"
            }
        }

        messageChain += improcMsgr[improcMsgr.lastIndex]
    } catch (e: Exception) {
        val msg = "格式化消息失败" + when(e) {
            is FileNotFoundException -> ", 文件未找到, 请检查路径是否正确"
            is IllegalArgumentException -> ", 非法参数传入"
            else -> ""
        }
        messageChain.clear()
        messageChain += PlainText("${msg}: ${e.message}")
        logger.error(e)
    }

    return messageChain.asMessageChain()
}

@JvmName("formatMessageMap")
suspend fun formatMessage(formatter: String, ctx: MessageContext? = null, map: Map<String, String>?): MessageChain {
    val newMap = map?.mapValues { PlainText(it.value) }
    return formatMessage(formatter, newMap, ctx)
}

suspend fun formatMessage(formatter: String, ctx: MessageContext? = null, vararg pair: Pair<String, Message>): MessageChain = formatMessage(formatter, pair.toMap(), ctx)

@JvmName("formatMessagePair")
suspend fun formatMessage(formatter: String, ctx: MessageContext? = null, vararg pair: Pair<String, String>): MessageChain = formatMessage(formatter, pair.toMap(), ctx)

@JvmName("formatMessageAny")
suspend fun formatMessage(formatter: String, ctx: MessageContext? = null, vararg pair: Pair<String, Any?>): MessageChain {
    val newMap = pair.toMap().mapValues {
        if (it.value is Message) it.value as Message
        else if (it.value is String) formatFace(it.value as String)
        else if (it.value is Number) formatFace(it.value.toString())
        else if (it.value is Boolean) formatFace(it.value.toString())
//        else throw Exception("the argument should be message")
        else it.value
    }
    return formatMessage(formatter, newMap, ctx)
}

suspend fun formatMessage(formatter: String, ctx: MessageContext? = null): MessageChain = formatMessage(formatter, null, ctx)
