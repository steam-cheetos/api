package io.andrewohara.cheetosbros.api.games.v1

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.*
import io.andrewohara.cheetosbros.api.users.User
import io.andrewohara.cheetosbros.lib.DynamoUtils
import io.andrewohara.cheetosbros.lib.InstantConverter
import io.andrewohara.cheetosbros.lib.PlatformConverter
import io.andrewohara.cheetosbros.sources.Game
import io.andrewohara.cheetosbros.sources.Player
import io.andrewohara.cheetosbros.sources.LibraryItem
import io.andrewohara.cheetosbros.sources.Platform
import java.time.Instant

class GameLibraryDao(tableName: String, client: AmazonDynamoDB) {

    val mapper = DynamoUtils.mapper<DynamoLibraryItem, String, String>(tableName, client)

    fun listGameIds(player: Player): Collection<String> {
        val query = DynamoDBQueryExpression<DynamoLibraryItem>()
                .withHashKeyValues(DynamoLibraryItem(playerUuid = uuid(player)))
                .withProjectionExpression("gameId")

        val result = mapper.query(query)

        return result.mapNotNull { it.gameId }
    }

    fun get(user: User, gameUuid: String): LibraryItem? {
        return mapper.load(user.id, gameUuid)?.toGameStatus()
    }

    fun batchSave(player: Player, libraryItems: Collection<LibraryItem>) {
        val existingGameIds = mapper.batchLoad(libraryItems.map { DynamoLibraryItem(player, it) }).map { it.gameId }

        val toSave = libraryItems.filter { it.gameId !in existingGameIds }.map { DynamoLibraryItem(player, it) }
        mapper.batchSave(toSave)
    }

    fun getAchievementStatusCacheDate(player: Player, game: Game): Instant? {
        val item = mapper.load(uuid(player), game.id) ?: return null
        return item.achievementStatusCacheDate
    }

    fun updateAchievementStatusCacheDate(player: Player, game: Game, time: Instant) {
        val item = mapper.load(uuid(player), game.id) ?: return
        mapper.save(item.copy(achievementStatusCacheDate = time))
    }

    @DynamoDBDocument
    data class DynamoLibraryItem(
            @DynamoDBHashKey
            var playerUuid: String? = null,

            @DynamoDBRangeKey
            var gameId: String? = null,

            @DynamoDBTypeConverted(converter = PlatformConverter::class)
            var platform: Platform? = null,

            @DynamoDBTypeConverted(converter = InstantConverter::class)
            var achievementStatusCacheDate: Instant? = null
    ) {
        constructor(player: Player, status: LibraryItem): this(
                playerUuid = "${player.platform}-${player.id}",
                gameId = status.gameId,
                platform = player.platform,
        )

        fun toGameStatus() = LibraryItem(platform = platform!!, gameId = gameId!!)
    }

    companion object {
        private fun uuid(player: Player) = "${player.platform}-${player.id}"
    }
}