package nucleus.commands

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import discord4j.common.util.Snowflake
import discord4j.core.`object`.entity.Member
import discord4j.core.`object`.entity.channel.TextChannel
import discord4j.rest.util.Color
import discord4j.rest.util.Permission
import harmony.Harmony
import harmony.command.CommandContext
import harmony.command.annotations.*
import io.javalin.Javalin
import reactor.core.publisher.Mono
import java.time.Instant

const val PORT = 7070
const val D4J_SERVER = "208023865127862272"
const val ADMIN_CHANNEL = "210938552563925002"
const val SPONSOR_ROLE = "717464998678364241"

@JsonClass(generateAdapter = true)
data class Sponsor(
    val login: String,
    val avatar_url: String,
    val html_url: String
)

@JsonClass(generateAdapter = true)
data class Tier(
    val description: String,
    val name: String,
    val monthly_price_in_dollars: Int
)

@JsonClass(generateAdapter = true)
data class Sponsorship(
    val sponsor: Sponsor,
    val privacy_level: String,  // 'public' for public
    val tier: Tier
)

@JsonClass(generateAdapter = true)
data class ChangeTier(
    val from: Tier
)

@JsonClass(generateAdapter = true)
data class PrivacyLevel(
    val from: String
)

@JsonClass(generateAdapter = true)
data class Changes(
    val tier: ChangeTier? = null,
    val privacy_level: PrivacyLevel? = null
)

@JsonClass(generateAdapter = true)
data class SponsorshipPayload(
    val action: String, // 'created' or 'pending_tier_change'
    val sponsorship: Sponsorship,
    val changes: Changes? = null
)


class Webhook(val harmony: Harmony) {

    val app: Javalin = Javalin.create().start(PORT)
    val moshi = Moshi.Builder().build()!!
    val sponsorshipAdapter = moshi.adapter(SponsorshipPayload::class.java)

    init {
        app.post("/webhook") { ctx ->
            val sponsorship = sponsorshipAdapter.fromJson(ctx.body())!!

            harmony.client.getChannelById(Snowflake.of(ADMIN_CHANNEL))
                .cast(TextChannel::class.java)
                .flatMap { it.createEmbed {
                    it.setAuthor(sponsorship.sponsorship.sponsor.login,
                        sponsorship.sponsorship.sponsor.html_url, sponsorship.sponsorship.sponsor.avatar_url)

                    it.setThumbnail("https://i.imgur.com/taLAFTG.png")
                    it.setColor(Color.PINK)
                    it.setTimestamp(Instant.now())

                    it.setDescription("${sponsorship.sponsorship.sponsor.login} is donating $${sponsorship.sponsorship.tier.monthly_price_in_dollars}/month\n" +
                            "This sponsorship is **${sponsorship.sponsorship.privacy_level}**.")

                    if (sponsorship.action == "created") {
                        it.setTitle("New Sponsorship!")
                    } else {
                        it.setTitle("Sponsorship Change")

                        if (sponsorship.changes?.privacy_level != null) {
                            it.addField("Privacy Change", "Originally: ${sponsorship.changes.privacy_level.from}", false)
                        } else if (sponsorship.changes?.tier != null) {
                            it.addField("Tier Change", "Originally: $${sponsorship.changes.tier.from.monthly_price_in_dollars}/month", false)
                        }
                    }

                } }.subscribe()
        }
    }
}

@ServerSpecific(D4J_SERVER)
@OnlyIn(ChannelType.SERVER)
@RequiresPermissions(Permission.ADMINISTRATOR)
@Help("Adds the Github Sponsor role to a member.")
@Command class SponsorCommand {

    @Help("Assigns the sponsor role to a member")
    @Responder fun sponsor(@Help("The member.") @Name("member") member: Member): Mono<String> {
        return member.addRole(Snowflake.of(SPONSOR_ROLE)).thenReturn("Done!")
    }
}