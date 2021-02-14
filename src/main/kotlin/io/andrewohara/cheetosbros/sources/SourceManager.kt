package io.andrewohara.cheetosbros.sources

import io.andrewohara.cheetosbros.api.games.v1.AchievementStatusDao
import io.andrewohara.cheetosbros.api.games.v1.AchievementsDao
import io.andrewohara.cheetosbros.api.games.v1.GameLibraryDao
import io.andrewohara.cheetosbros.api.games.v1.GamesDao
import io.andrewohara.cheetosbros.api.users.User
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors

class SourceManager(
    private val sourceFactory: SourceFactory,
    private val gamesDao: GamesDao,
    private val gameLibraryDao: GameLibraryDao,
    private val achievementsDao: AchievementsDao,
    private val achievementStatusDao: AchievementStatusDao
    ) {

    private val syncExecutor = Executors.newFixedThreadPool(10)

    private val log = LoggerFactory.getLogger(javaClass)

    fun sync(user: User, player: Player) {
        val source = sourceFactory[user, player.platform] ?: let {
            log.warn("No ${player.platform} source found for ${user.displayName}")
            return
        }

        sync(player, source)
    }

    fun sync(player: Player, source: Source) {
        log.info("Action=SyncPlayerStart Player=${player.platform}-${player.username}")

        val games = source.library(player.id)

        for (game in games) {
            syncExecutor.run {
                syncGame(source, player, game)
            }
        }

        log.info("Action=SyncPlayerComplete Player=${player.platform}-${player.username}")
    }

    private fun syncGame(source: Source, player: Player, game: Game) {
        log.debug("Action=SyncGameStart Player=${player.platform}-${player.username} Game=${game.platform}-${game.id}")

        val achievements = source.achievements(game.id)
        if (achievements.isEmpty()) {
            log.debug("Action=SyncGameAbort Player=${player.platform}-${player.username} Game=${game.platform}-${game.id} Reason=achievements.none")
            return
        }

        val progress = source.userAchievements(game.id, player.id)

        gamesDao.save(game)
        gameLibraryDao.save(player, game)
        achievementsDao.batchSave(game, achievements)
        achievementStatusDao.batchSave(player, game, progress)

        log.debug("Action=SyncGameComplete Player=${player.platform}-${player.username} Game=${game.platform}-${game.id} Achievements=${achievements.size}")
    }
}