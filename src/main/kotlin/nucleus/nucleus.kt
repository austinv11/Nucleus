package nucleus

import com.austinv11.servicer.WireService
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import discord4j.discordjson.json.gateway.StatusUpdate
import discord4j.common.util.Snowflake
import discord4j.core.`object`.presence.Activity
import discord4j.core.`object`.presence.Presence
import harmony.Harmony
import harmony.command.CommandOptions
import harmony.command.command
import harmony.command.interfaces.HarmonyEntryPoint
import harmony.command.interfaces.PrefixProvider
import harmony.util.Feature
import nucleus.commands.Webhook
import nucleus.commands.initializeChronicles
import nucleus.util.DB
import reactor.core.publisher.Mono
import java.time.Duration
import java.util.*

// For testing
fun main(args: Array<String>) {
    Harmony(args[0], Feature.enable(CommandOptions(prefix = ConfigurablePrefixProvider))).awaitClose()
}

const val DEFAULT_PREFIX = "?"

object ConfigurablePrefixProvider : PrefixProvider {

    val guildPrefixCache: Cache<Long, Optional<String>> = Caffeine.newBuilder()
            .maximumSize(1000)
            .build()
    val dmPrefixCache: Cache<Long, Optional<String>> = Caffeine.newBuilder()
            .maximumSize(100)
            .expireAfterAccess(Duration.ofMinutes(30)) // bots are less likely to be used consistently in dms
            .build()

    override fun getDmPrefix(authorId: Snowflake): Optional<String> {
        val dmPrefix = dmPrefixCache.getIfPresent(authorId.asLong())

        if (dmPrefix != null) return dmPrefix

        return DB.getPrefix(authorId)
                .switchIfEmpty(Mono.just(Optional.of(DEFAULT_PREFIX)))
                .doOnNext {
                    dmPrefixCache.put(authorId.asLong(), it)
                }
                .block()!!
    }

    override fun getGuildPrefix(guildId: Snowflake, channelId: Snowflake): Optional<String> {
        val guildPrefix = guildPrefixCache.getIfPresent(guildId.asLong())

        if (guildPrefix != null) return guildPrefix

        return DB.getPrefix(guildId)
                .switchIfEmpty(Mono.just(Optional.of(DEFAULT_PREFIX)))
                .doOnNext {
                    guildPrefixCache.put(guildId.asLong(), it)
                }
                .block()!!
    }

    fun setDmPrefix(authorId: Snowflake, prefix: String?) {
        DB.setPrefix(prefix, false, authorId)
                .subscribe { dmPrefixCache.put(authorId.asLong(), Optional.ofNullable(prefix)) }
    }

    fun setGuildPrefix(guildId: Snowflake, prefix: String?) {
        DB.setPrefix(prefix, true, guildId)
                .subscribe { guildPrefixCache.put(guildId.asLong(), Optional.ofNullable(prefix)) }
    }
}

@WireService(HarmonyEntryPoint::class)
class NucleusEntryPoint : HarmonyEntryPoint {

    override fun buildHarmony(token: String): Harmony {
        return Harmony(token, Feature.enable(CommandOptions(
            prefix = ConfigurablePrefixProvider
        )))
    }

    override fun startBot(harmony: Harmony): HarmonyEntryPoint.ExitSignal {
        initializeChronicles(harmony).subscribe()

        val webhook = Webhook(harmony)

        harmony.owner.privateChannel
                .flatMap { it.createMessage("I've just started up!") }
                .delayElement(Duration.ofSeconds(5))
                .doOnNext {
                    harmony.status = Presence.online(Activity.playing("Type @${harmony.self.username} help"))
                }
                .subscribe()

        val exit = harmony.client.onDisconnect()
            .map { HarmonyEntryPoint.ExitSignal.COMPLETE_CLOSE }
            .onErrorContinue { t, u -> HarmonyEntryPoint.ExitSignal.ABNORMAL_CLOSE }
            .or(Mono.create { sink ->
                harmony.command("restart") {
                    description = "Restarts the bot."
                    botOwnerOnly = true

                    responder {
                        description = "Attempts to restart the bot ASAP."

                        handle {
                            context.channel.createMessage("I'll be right back!").then(Mono.fromRunnable<Void> {
                                DB.mongo.close()
                                sink.success(HarmonyEntryPoint.ExitSignal.RESTART)
                            })
                        }
                    }
                }

                harmony.command("shutdown") {
                    description = "Shuts down the bot."
                    botOwnerOnly = true

                    responder {
                        description = "Attempts to shut down the bot ASAP."

                        handle {
                            context.channel.createMessage("Mr. Stark, I don't feel so good...")
                                .then(Mono.fromRunnable<Void> {
                                    DB.mongo.close()
                                    sink.success(HarmonyEntryPoint.ExitSignal.COMPLETE_CLOSE)
                                })
                        }
                    }
                }
            })
            .block()!!
        return exit
    }

    override fun getToken(programArgs: Array<out String>): String {
        return super.getToken(programArgs)
    }
}