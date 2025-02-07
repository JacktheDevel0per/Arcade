package net.casual.arcade.minigame.managers

import net.casual.arcade.chat.ChatFormatter
import net.casual.arcade.chat.PlayerChatFormatter
import net.casual.arcade.events.player.PlayerChatEvent
import net.casual.arcade.minigame.Minigame
import net.casual.arcade.minigame.annotation.NONE
import net.casual.arcade.utils.ComponentUtils.literal
import net.casual.arcade.utils.ComponentUtils.red
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

public class MinigameChatManager(
    private val minigame: Minigame<*>
) {
    public var globalChatFormatter: PlayerChatFormatter = PlayerChatFormatter.GLOBAL
    public var teamChatFormatter: PlayerChatFormatter = PlayerChatFormatter.TEAM
    public var adminChatFormatter: PlayerChatFormatter = PlayerChatFormatter.ADMIN
    public var spectatorChatFormatter: PlayerChatFormatter = PlayerChatFormatter.SPECTATOR
    public var regularChatFormatter: PlayerChatFormatter? = PlayerChatFormatter.GLOBAL

    public var systemChatFormatter: ChatFormatter? = null

    public var mutedMessage: Component = "Currently chat is muted".literal().red()

    init {
        this.minigame.events.register<PlayerChatEvent>(1_000, NONE, this::onGlobalPlayerChat)
        this.minigame.events.register<PlayerChatEvent> { this.onPlayerChat(it) }
    }

    public fun broadcast(message: Component, formatter: ChatFormatter? = this.systemChatFormatter) {
        this.broadcastTo(message, this.minigame.getAllPlayers(), formatter)
    }

    public fun broadcastTo(
        message: Component,
        players: Collection<ServerPlayer>,
        formatter: ChatFormatter? = this.systemChatFormatter
    ) {
        val formatted = formatter?.format(message) ?: message
        for (player in players) {
            player.sendSystemMessage(formatted)
        }
    }

    public fun broadcastTo(
        message: Component,
        player: ServerPlayer,
        formatter: ChatFormatter? = this.systemChatFormatter
    ) {
        val formatted = formatter?.format(message) ?: message
        player.sendSystemMessage(formatted)
    }

    public fun isMessageGlobal(sender: ServerPlayer, message: String): Boolean {
        val team = sender.team
        return team == null || this.minigame.teams.isTeamIgnored(team) || message.startsWith("!")
    }

    private fun onGlobalPlayerChat(event: PlayerChatEvent) {
        if (!this.minigame.settings.isChatGlobal && !this.minigame.hasPlayer(event.player)) {
            event.addFilter { !this.minigame.hasPlayer(it) }
        }
    }

    private fun onPlayerChat(event: PlayerChatEvent) {
        val (player, message) = event
        if (this.minigame.settings.isChatMuted.get(player)) {
            event.cancel()
            this.broadcastTo(this.mutedMessage, player)
            return
        }

        if (this.minigame.isAdmin(player)) {
            val (decorated, prefix) = this.adminChatFormatter.format(player, message.decoratedContent())
            event.replaceMessage(decorated, prefix)
            return
        }

        if (this.minigame.isSpectating(player)) {
            val (decorated, prefix) = this.spectatorChatFormatter.format(player, message.decoratedContent())
            event.replaceMessage(decorated, prefix)
            return
        }

        if (!this.minigame.settings.isTeamChat) {
            val formatter = this.regularChatFormatter
            if (formatter != null) {
                val (decorated, prefix) = formatter.format(player, message.decoratedContent())
                event.replaceMessage(decorated, prefix)
            }
            return
        }

        val team = player.team

        val content = event.rawMessage
        val exclaimed = content.startsWith("!")
        if (exclaimed || team == null || this.minigame.teams.isTeamIgnored(team)) {
            val trimmed = if (exclaimed) content.substring(1) else content
            if (trimmed.isNotBlank()) {
                val (decorated, prefix) = this.globalChatFormatter.format(player, trimmed.trim().literal())
                event.replaceMessage(decorated, prefix)
            } else {
                event.cancel()
            }
            return
        }
        val (decorated, prefix) = this.teamChatFormatter.format(player, message.decoratedContent())
        event.replaceMessage(decorated, prefix)
        event.addFilter { team == it.team }
        return
    }
}