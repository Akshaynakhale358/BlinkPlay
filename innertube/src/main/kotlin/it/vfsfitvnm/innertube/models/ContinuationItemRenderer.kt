package it.vfsfitvnm.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class ContinuationItemRenderer(
    val continuationEndpoint: ContinuationEndpoint? = null
) {
    @Serializable
    data class ContinuationEndpoint(
        val continuationCommand: ContinuationCommand? = null,
        val commandExecutorCommand: CommandExecutorCommand? = null
    ) {
        fun token(): String? =
            continuationCommand?.token
                ?: commandExecutorCommand?.commands
                    ?.firstNotNullOfOrNull { it.continuationCommand?.token }
    }

    @Serializable
    data class CommandExecutorCommand(
        val commands: List<Command>? = null
    )

    @Serializable
    data class Command(
        val continuationCommand: ContinuationCommand? = null
    )

    @Serializable
    data class ContinuationCommand(
        val token: String? = null,
        val request: String? = null
    )
}
