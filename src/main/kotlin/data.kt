package bot.good

import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.event.events.MessageEvent

data class IDResource (val id: Int, val zhName: String, val jpName: String, val enName: String)

data class MessageContext(
    val contact: Contact? = null,
    val event: MessageEvent? = null,
    val errorImageReplacePath: String? = null
)

