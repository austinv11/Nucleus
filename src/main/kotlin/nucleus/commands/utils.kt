package nucleus.commands

import discord4j.rest.util.Image
import discord4j.rest.util.Permission
import harmony.command.CommandContext
import harmony.command.annotations.*
import jep.JepException
import jep.SharedInterpreter
import nucleus.ConfigurablePrefixProvider
import nucleus.util.PythonCommandContext
import reactor.core.publisher.Mono
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit


@BotOwnerOnly // Private bot for now
@Help("Generates an invite link for the bot.")
@Command class InviteCommand {

    @Help("Generates an invite link to invite this bot to your server.")
    @Responder fun invite(context: CommandContext): Mono<String> {
        return context.harmony.client.applicationInfo
                .map { "https://discord.com/oauth2/authorize?client_id=${it.id.asString()}&scope=bot&permissions=8" }

    }
}

@Help("Calculates latency between message sending and command invocation.")
@Command class PingCommand {

    @Help("Calculates the latency.")
    @Responder fun ping(context: CommandContext): Mono<Void> {
        val originalTime = System.currentTimeMillis() - context.message.timestamp.toEpochMilli()
        val originalContent = "Time to process the command message: `$originalTime` ms"
        return context.channel.createMessage(originalContent)
            .flatMap {
                val sendTime = System.currentTimeMillis() - it.timestamp.toEpochMilli()
                it.edit { spec ->
                    spec.setContent("$originalContent\nTime to send message: `$sendTime` ms\nTotal latency: `${sendTime +originalTime }` ms")
                }
            }.then()
    }
}

@Help("Reports the current uptime.")
@Command class UptimeCommand {

    @Help("Reports the uptime.")
    @Responder fun uptime(): String {
        val uptime = ManagementFactory.getRuntimeMXBean().uptime
        val seconds = TimeUnit.MILLISECONDS.toSeconds(uptime)
        val s: Long = seconds % 60
        val m: Long = seconds / 60 % 60
        val h: Long = seconds / (60 * 60) % 24

        return "I've been online for `$h` hours, `$m` minutes, and `$s` seconds!"
    }
}

@RequiresPermissions(Permission.ADMINISTRATOR)
@Help("Gets or sets a command prefix for the current server.")
@Command class PrefixCommand {

    @Help("Gets the current prefix")
    @Responder fun getPrefix(context: CommandContext): String {
        val prefix = if (context.server != null)
            ConfigurablePrefixProvider.getGuildPrefix(context.server!!.id, context.channel.id)
        else
            ConfigurablePrefixProvider.getDmPrefix(context.author.id)

        return if (prefix.isPresent)
            "The current prefix is: `${prefix.get()}`"
        else
            "No prefix is set!"
    }

    @Help("Sets the current prefix")
    @Responder fun setPrefix(context: CommandContext,
                             @Help("The prefix to now use or 'remove' to delete the current prefix")
                             @Name("prefix") prefix: String): String {
        val newPrefix = if (prefix != "remove") prefix else null

        if (context.server != null)
            ConfigurablePrefixProvider.setGuildPrefix(context.server!!.id, newPrefix)
        else
            ConfigurablePrefixProvider.setDmPrefix(context.author.id, newPrefix)

        return if (newPrefix != null)
            return "Prefix has been set to `$newPrefix`"
        else
            return "Prefix has been cleared!"
    }
}

@BotOwnerOnly
@Help("Sets the bot's avatar image.")
@Command class AvatarCommand {

    @Help("Sets the bot's avatar image.")
    @Responder fun avatar(context: CommandContext, @Help("The url for the image.") @Name("avatar url") url: String): Mono<String> {
        return Image.ofUrl(url).flatMap { image -> context.client.edit { it.setAvatar(image) } }.thenReturn("Done!")
    }
}


@BotOwnerOnly
@Help("Sets the bot's username.")
@Command class RenameCommand {

    @Help("Sets the bot's username.")
    @Responder fun rename(context: CommandContext, @Help("The new username.") @Name("username") name: String): Mono<String> {
        return context.client.edit { it.setUsername(name) }.thenReturn("Done!")
    }
}


@BotOwnerOnly
@Alias("python3")
@Help("Runs a python snippet in a python3.7 interpreter WIP.")
@Command class PythonCommand {

    private fun prepareInterpreter(interpreter: SharedInterpreter, context: CommandContext) {
        interpreter.set("context", PythonCommandContext(context))
    }

    private fun runScript(interpreter: SharedInterpreter, script: String): Mono<String> {
        val complete = "import discord4j.rest.util.Snowflake\ndef snowflake(id):    return Snowflake.of(id)\ndef _invoke():\n" + script.lines()
            .map { "    $it" }.joinToString("\n")
        interpreter.exec(complete)
        try {
            return Mono.justOrEmpty(interpreter.invoke("_invoke")?.toString())
        } catch (e: JepException) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            return Mono.just(sw.toString())
        }
    }

        @Help("Executes arbitrary python code.")
        @Responder fun execute(context: CommandContext,
                               @Help("The python code to run.") @Name("script") script: String): Mono<Void> {
            return context.channel.typeUntil(
                Mono.just(script)
                    .map {
                        it.removePrefix("```python3")
                            .removePrefix("```python")
                            .removePrefix("```py3")
                            .removePrefix("```py")
                            .removePrefix("```")
                            .removePrefix("`")
                            .removeSuffix("```")
                            .removeSuffix("`")
                            .trim()
                    }.flatMap { script ->
                        Mono.using(
                            { SharedInterpreter() },
                            {
                                prepareInterpreter(it, context)
                                runScript(it, script)
                            },
                            { it.close() })
                    }.flatMap { context.channel.createMessage("Python output:\n```$it```") }
            ).then()
        }
}