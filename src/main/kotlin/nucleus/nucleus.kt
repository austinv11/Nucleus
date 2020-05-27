package nucleus

import com.austinv11.servicer.WireService
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import discord4j.rest.util.Snowflake
import discord4j.store.api.util.LongLongTuple2
import harmony.Harmony
import harmony.command.CommandOptions
import harmony.command.command
import harmony.command.interfaces.HarmonyEntryPoint
import harmony.command.interfaces.PrefixProvider
import harmony.util.Feature
import nucleus.util.fileExists
import nucleus.util.open
import reactor.core.publisher.Mono
import java.util.*
import kotlin.collections.HashMap

// For testing
fun main(args: Array<String>) {
    Harmony(args[0], Feature.enable(CommandOptions())).awaitClose()
}

data class IdPrefixPair(val id: Long, val prefix: String?)

data class PrefixConfig(val dms: Array<IdPrefixPair>, val guilds: Array<IdPrefixPair>)

object ConfigurablePrefixProvider : PrefixProvider {
    private val gson = GsonBuilder().setPrettyPrinting().create() //FIXME: this won't scale

    private val guildPrefixes = HashMap<Long, String?>()
    private val dmPrefixes = HashMap<Long, String?>()

    init {
        if (fileExists("prefixes.json")) { // TODO: Make a volume in Dockerfile
            open("prefixes.json", 'r') { f ->
                val config = gson.fromJson(f.read(), PrefixConfig::class.java)!!
                config.dms.forEach { pair ->
                    setDmPrefix(Snowflake.of(pair.id), pair.prefix)
                }
                config.guilds.forEach { pair ->
                    setDmPrefix(Snowflake.of(pair.id), pair.prefix)
                }
            }
        }
    }

    override fun getDmPrefix(authorId: Snowflake)
            = Optional.ofNullable(dmPrefixes.getOrDefault(authorId.asLong(), ">"))

    override fun getGuildPrefix(guildId: Snowflake, channelId: Snowflake)
            = Optional.ofNullable(guildPrefixes.getOrDefault(guildId.asLong(), ">"))

    @Synchronized
    fun setDmPrefix(authorId: Snowflake, prefix: String?) {
        dmPrefixes[authorId.asLong()] = prefix
        save()
    }

    @Synchronized
    fun setGuildPrefix(guildId: Snowflake, prefix: String?) {
        guildPrefixes[guildId.asLong()] = prefix
        save()
    }

    fun save() {
        open("prefixes.json", 'w') { f ->
            f.write(gson.toJson(PrefixConfig(dmPrefixes.map { IdPrefixPair(it.key, it.value) }.toTypedArray(),
                guildPrefixes.map { IdPrefixPair(it.key, it.value) }.toTypedArray())))
        }
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
        harmony.owner.privateChannel.flatMap { it.createMessage("I've just started up!") }.subscribe()

        return harmony.client.onDisconnect()
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
                                    sink.success(HarmonyEntryPoint.ExitSignal.COMPLETE_CLOSE)
                                })
                        }
                    }
                }
            })
            .block()!!
    }

    override fun getToken(programArgs: Array<out String>): String {
        return super.getToken(programArgs)
    }
}