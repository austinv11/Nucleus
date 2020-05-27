package nucleus.util

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.Region
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.Message
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.event.domain.Event
import discord4j.rest.util.Snowflake
import harmony.Harmony
import harmony.command.CommandContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

// TODO
data class PythonCommandContext(@JvmField val context: CommandContext) {

    @JvmField
    val client = PyClient(context.harmony, context.client)

    @JvmField
    val message = PyMessage(context.message)

    @JvmField
    val channel = PyChannel(context.channel)

    @JvmField
    val author = PyUser(context.author)

    @JvmField
    val guild: PyGuild? = if (context.server == null) null else PyGuild(context.server!!)

    fun snowflake(id: String) = Snowflake.of(id)
}

data class PyClient(private val harmony: Harmony, private val discordClient: GatewayDiscordClient) {

    @JvmField
    val users: Flux<User> = discordClient.users

    @JvmField
    val regions: Flux<Region> = discordClient.regions

    @JvmField
    val guilds: Flux<Guild> = discordClient.guilds

    @JvmField
    val channels: Flux<Channel> = discordClient.guilds.flatMap { it.channels }

    @JvmField
    val self = harmony.self

    fun on(event: String): Flux<Event> = discordClient.on(Class.forName(event) as Class<Event>)

    fun channelById(id: Snowflake): Mono<Channel> = discordClient.getChannelById(id)

    fun guildById(id: Snowflake): Mono<Guild> = discordClient.getGuildById(id)

    fun userById(id: Snowflake): Mono<User> = discordClient.getUserById(id)
}

data class PyMessage(private val message: Message) {

    @JvmField
    val content = message.content

    @JvmField
    val id = message.id
}

data class PyChannel(private val channel: MessageChannel) {

}

data class PyUser(private val user: User) {

}

data class PyGuild(private val guild: Guild) {

}