package nucleus.commands

import discord4j.core.`object`.entity.channel.MessageChannel
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.core.event.domain.guild.MemberJoinEvent
import discord4j.rest.util.Permission
import discord4j.common.util.Snowflake
import discord4j.rest.util.Color
import harmony.Harmony
import harmony.command.CommandContext
import harmony.command.annotations.*
import nucleus.util.DB
import reactor.core.Disposable
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

val chronicles = ConcurrentHashMap<Snowflake, Pair<Snowflake, Disposable>>()

fun initializeChronicles(harmony: Harmony): Mono<Void> {
    return DB.getChronicles()
        .flatMap {
            return@flatMap harmony.client.getGuildById(Snowflake.of(it.guildId)).flatMap { g -> g.getChannelById(
                Snowflake.of(it.channelId))
            }
        }
        .map { c -> Triple(c.id, c.guildId, hookChronicle(harmony, c.guildId, c as TextChannel)) }
        .doOnNext { chronicles[it.second] = it.first to it.third }
        .then()
}

fun hookChronicle(harmony: Harmony, guildId: Snowflake, channel: MessageChannel): Disposable {
    return channel.client.on(MemberJoinEvent::class.java)
        .filter { it.guildId == guildId }
        .flatMap { event ->
            val now = Instant.now()
            val elapsed = Duration.between(event.member.id.timestamp, now)
            channel.createEmbed {
                it.setThumbnail(event.member.avatarUrl)
                it.setTitle("${event.member.username}#${event.member.discriminator}")
                it.setDescription("Account age: `${elapsed.toDaysPart()}` days, `${elapsed.toHoursPart()}` hours, `${elapsed.toMinutesPart()}` minutes, `${elapsed.toSecondsPart()}` seconds")
                it.setTimestamp(now)
                it.setColor(Color.RED)
                it.setAuthor("Nucleus Chronicle", null, harmony.self.avatarUrl)
                it.setFooter("Invoke the chronicle command to stop", null)
            }
        }
        .subscribe()
}

@OnlyIn(ChannelType.SERVER)
@RequiresPermissions(Permission.MANAGE_GUILD)
@Help("Records a running log of new users to the server. This is useful for manually detecting bot net users.")
@Command class ChronicleCommand {

    @Help("Toggles the member joining log within the current channel.")
    @Responder fun chronicle(context: CommandContext): Mono<String> {
        val channel = context.channel
        val guild = context.server!!

        if (!chronicles.containsKey(guild.id)) {
            return DB.putChronicle(guild.id, channel.id)
                .then(Mono.fromSupplier {
                    chronicles[guild.id] = channel.id to hookChronicle(context.harmony, guild.id, channel)
                    "Nucleus Chronicle will now log member joins here."
                })
        } else if (chronicles.containsKey(guild.id) && chronicles[guild.id]!!.first == channel.id) {
            return DB.removeChronicle(guild.id)
                .then(Mono.fromRunnable<Void> {
                    val disposable = chronicles[guild.id]!!.second
                    chronicles.remove(guild.id)
                    disposable.dispose()
                })
                .thenReturn("Nucleus Chronicle will no longer log member joins.")
        } else {
            return DB.putChronicle(guild.id, channel.id).then(Mono.fromRunnable<Void> {
                val disposable = chronicles[guild.id]!!.second
                disposable.dispose()
            }).then(Mono.fromSupplier {
                    chronicles[guild.id] = channel.id to hookChronicle(context.harmony, guild.id, channel)
                    "Nucleus Chronicle has been moved to this channel."
            })
        }
    }
}