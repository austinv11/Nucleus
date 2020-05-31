package nucleus.util

import com.mongodb.reactivestreams.client.MongoClient
import com.mongodb.reactivestreams.client.MongoDatabase
import discord4j.common.util.Snowflake
import org.bson.codecs.pojo.annotations.BsonId
import org.litote.kmongo.eq
import org.litote.kmongo.match
import org.litote.kmongo.reactivestreams.*
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import reactor.core.publisher.toMono
import java.util.*

data class PrefixDocument(val prefix: String, val isGuild: Boolean, @BsonId val id: String)

data class ChronicleDocument(@BsonId val guildId: String, val channelId: String)

val NULL_PREFIX_HOLDER = "<NULL>"

object DB {

    val mongo: MongoClient = KMongo.createClient()
    val database: MongoDatabase

    init {
        database = mongo.getDatabase("nucleusDB")
        database.createCollection("prefixCollection").toMono().subscribe()
        database.createCollection("chronicleCollection").toMono().subscribe()
    }

    fun setPrefix(prefix: String?, isGuild: Boolean, id: Snowflake): Mono<Void> {
        val collection = database.getCollectionOfName<PrefixDocument>("prefixCollection")
        return collection.deleteOneById(id.asString()).toMono()
            .then(collection.insertOne(PrefixDocument(prefix ?: NULL_PREFIX_HOLDER, isGuild, id.asString())).toMono())
            .then()
    }

    fun getPrefix(id: Snowflake): Mono<Optional<String>> { // Will be empty if there is no record
        val collection = database.getCollectionOfName<PrefixDocument>("prefixCollection")
        return collection.findOne(PrefixDocument::id eq id.asString())
            .toMono().map { if (it.prefix == NULL_PREFIX_HOLDER) Optional.empty() else Optional.of(it.prefix) }
    }

    fun getChronicles(): Flux<ChronicleDocument> {
        return database.getCollectionOfName<ChronicleDocument>("chronicleCollection")
            .find().toFlux()
    }

    fun getChronicle(guildId: Snowflake): Mono<ChronicleDocument> {
        return database.getCollectionOfName<ChronicleDocument>("chronicleCollection")
            .findOneById(guildId.asString()).toMono()
    }

    fun removeChronicle(guildId: Snowflake): Mono<Void> {
        return database.getCollectionOfName<ChronicleDocument>("chronicleCollection")
            .deleteOneById(guildId.asString()).toMono().then()
    }

    fun putChronicle(guildId: Snowflake, channelId: Snowflake): Mono<Void> {
        val collection = database.getCollectionOfName<ChronicleDocument>("chronicleCollection")
        return collection
            .deleteOneById(guildId.asString()).toMono()
            .then(collection.insertOne(ChronicleDocument(guildId.asString(), channelId.asString())).toMono())
            .then()
    }
}