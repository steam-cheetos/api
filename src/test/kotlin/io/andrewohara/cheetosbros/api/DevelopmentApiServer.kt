package io.andrewohara.cheetosbros.api

import io.andrewohara.cheetosbros.api.auth.KmsAuthorizationDao
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Uri
import org.http4k.core.then
import org.http4k.filter.*
import org.http4k.server.SunHttp
import org.http4k.server.asServer
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient
import software.amazon.awssdk.services.kms.KmsClient
import java.time.Clock
import kotlin.io.path.ExperimentalPathApi

object DevelopmentApiServer {

    @ExperimentalPathApi
    @JvmStatic
    fun main(args: Array<String>) {
        val env = System.getenv()

        val dynamo = DynamoDbEnhancedClient.create()
        val kms = KmsClient.builder()
            .credentialsProvider(ProfileCredentialsProvider.create("default"))
            .build()

        val serverHost = Uri.of(env.getValue("SERVER_HOST"))

        val gameService = ServiceBuilder.gameService(
            dynamo = dynamo,
            achievementsTableName = env.getValue("ACHIEVEMENTS_TABLE"),
            gamesTableName = env.getValue("GAMES_TABLE")
        )

        val syncService = ServiceBuilder.syncService(
            steamBackend = DebuggingFilters.PrintResponse().then(JavaHttpClient()),
            steamApiKey = env.getValue("STEAM_API_KEY"),
            clock = Clock.systemUTC(),
            gameService = gameService
        )

        val jobService = ServiceBuilder.jobService(
            dynamo = dynamo,
            jobsTableName = env.getValue("JOBS_TABLE"),
            clock = Clock.systemUTC(),
            syncService = syncService
        )

        val api = ServiceBuilder.api(
            gameService = gameService,
            authService = ServiceBuilder.authService(
                serverHost = serverHost,
                authDao = KmsAuthorizationDao(
                    kms = kms,
                    keyId = env.getValue("AUTH_KEY_ID")
                )
            ),
            jobService = jobService,
            syncService = syncService,
            corsPolicy = CorsPolicy(OriginPolicy.AllowAll(), listOf("Authorization"), Method.values().toList(), false)
        )

        DebuggingFilters.PrintRequestAndResponse()
            .then(api)
            .asServer(SunHttp(serverHost.port!!))
            .start()
            .block()
    }
}