package io.andrewohara.cheetosbros.api.games.v1

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey
import io.andrewohara.cheetosbros.lib.DynamoUtils
import io.andrewohara.cheetosbros.sources.Achievement
import io.andrewohara.cheetosbros.sources.Game

class AchievementsDao(tableName: String, client: AmazonDynamoDB) {

    val mapper = DynamoUtils.mapper<DynamoAchievement, String, String>(tableName, client)

    fun list(game: Game): Collection<Achievement> {
        val query = DynamoDBQueryExpression<DynamoAchievement>()
                .withHashKeyValues(DynamoAchievement(gameUuid = game.uuid))

        return mapper.query(query).map { it.toAchievement() }
    }

    fun batchSave(game: Game, achievements: Collection<Achievement>) {
        val items = achievements.map { DynamoAchievement(game, it) }
        mapper.batchSave(items)
    }

    @DynamoDBDocument
    data class DynamoAchievement(
            @DynamoDBHashKey
            var gameUuid: String? = null,

            @DynamoDBRangeKey
            var achievementId: String? = null,

            var name: String? = null,
            var description: String? = null,
            var hidden: Int? = null,
            var icons: List<String> = emptyList(),
            var score: Int? = null,
    ) {
        constructor(game: Game, achievement: Achievement): this(
                gameUuid = game.uuid,
                achievementId = achievement.id,
                name = achievement.name,
                description = achievement.description,
                hidden = if (achievement.hidden) 1 else 0,
                icons = achievement.icons,
                score = achievement.score
        )

        fun toAchievement() = Achievement(
                id = achievementId!!,
                name = name!!,
                description = description,
                hidden = hidden == 1,
                icons = icons,
                score = score
        )
    }
}