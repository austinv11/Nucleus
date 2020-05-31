package nucleus.util

import discord4j.core.GatewayDiscordClient
import discord4j.core.`object`.Region
import discord4j.core.`object`.entity.Guild
import discord4j.core.`object`.entity.User
import discord4j.core.`object`.entity.channel.Channel
import discord4j.core.event.domain.Event
import discord4j.common.util.Snowflake
import harmony.Harmony
import harmony.command.CommandContext
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

data class PythonCommandContext(@JvmField val context: CommandContext) {

    @JvmField
    val client = PyClient(context.harmony, context.client)

    @JvmField
    val message = context.message

    @JvmField
    val channel = context.channel

    @JvmField
    val author = context.author

    @JvmField
    val guild: Guild? = context.server

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