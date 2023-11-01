package net.casual.arcade.minigame.lobby

import com.google.gson.JsonObject
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.casual.arcade.Arcade
import net.casual.arcade.commands.arguments.MinigameArgument
import net.casual.arcade.events.minigame.MinigameAddNewPlayerEvent
import net.casual.arcade.events.player.PlayerTickEvent
import net.casual.arcade.gui.ready.ReadyChecker
import net.casual.arcade.minigame.Minigame
import net.casual.arcade.minigame.MinigamePhase
import net.casual.arcade.utils.CommandUtils.commandSuccess
import net.casual.arcade.utils.CommandUtils.fail
import net.casual.arcade.utils.CommandUtils.success
import net.casual.arcade.utils.ComponentUtils.green
import net.casual.arcade.utils.ComponentUtils.literal
import net.casual.arcade.utils.ComponentUtils.singleUseFunction
import net.casual.arcade.utils.MinigameUtils.arePlayersReady
import net.casual.arcade.utils.MinigameUtils.areTeamsReady
import net.casual.arcade.utils.MinigameUtils.countdown
import net.casual.arcade.utils.PlayerUtils.broadcast
import net.casual.arcade.utils.PlayerUtils.broadcastToOps
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.scores.PlayerTeam

public abstract class LobbyMinigame(
    server: MinecraftServer
): Minigame<LobbyMinigame>(server), ReadyChecker {
    protected abstract val lobby: Lobby

    private var awaiting: (() -> Component)? = null
    private var next: Minigame<*>? = null

    init {
        this.initialise()
    }

    override fun initialise() {
        super.initialise()
        this.events.register<MinigameAddNewPlayerEvent> { (_, player) ->
            this.lobby.forceTeleportToSpawn(player)
        }
        this.events.register<PlayerTickEvent> { (player) ->
            this.lobby.tryTeleportToSpawn(player)
        }

        this.commands.register(this.createLobbyCommand())

        this.pvp = false
        this.hunger = false
    }

    public open fun onStart() {

    }

    public open fun onStartCountdown() {

    }

    public open fun getTeamsToReady(): Collection<PlayerTeam> {
        return this.getPlayerTeams()
    }

    public open fun getPlayersToReady(): Collection<ServerPlayer> {
        return this.getPlayers()
    }

    override fun onReady() {
        this.awaiting = null
        val component = "All players are ready, click to start!".literal().green().singleUseFunction {
            this.setPhase(Phases.Countdown)
        }
        this.getPlayers().broadcastToOps(component, 4)
    }

    override fun broadcast(message: Component) {
        this.getPlayers().broadcast(message)
    }

    protected open fun moveToNextMinigame() {
        val next = this.next!!
        for (player in this.getPlayers()) {
            next.addPlayer(player)
        }
        next.start()

        this.close()
    }

    public fun getNextMinigame(): Minigame<*>? {
        return this.next
    }

    public fun setNextMinigame(minigame: Minigame<*>) {
        this.next = minigame
    }

    final override fun start() {
        this.setPhase(Phases.Waiting)
    }

    final override fun getPhases(): List<Phases> {
        return listOf(Phases.Waiting, Phases.Countdown)
    }

    final override fun getLevels(): Collection<ServerLevel> {
        return listOf(this.lobby.spawn.level)
    }

    override fun appendAdditionalDebugInfo(json: JsonObject) {
        super.appendAdditionalDebugInfo(json)
        val next = this.next
        if (next != null) {
            json.add("next_minigame", next.getDebugInfo())
        }
    }

    protected open fun createLobbyCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal("lobby").then(
            Commands.literal("next").then(
                Commands.literal("settings").executes(this::nextMinigameSettings)
            ).then(
                Commands.literal("set").then(
                    Commands.literal("existing").then(
                        Commands.argument("minigame", MinigameArgument.minigame()).executes(this::setNextMinigame)
                    )
                ).then(
                    Commands.literal("new").then(
                        Commands.argument("minigame", MinigameArgument.Factory.factory()).executes(this::setNextNewMinigame)
                    )
                )
            ).then(
                Commands.literal("unset").executes(this::unsetNextMinigame)
            )
        ).then(
            Commands.literal("reload").executes(this::reloadLobby)
        ).then(
            Commands.literal("delete").executes(this::deleteLobby)
        ).then(
            Commands.literal("tp").executes(this::teleportToLobby)
        ).then(
            Commands.literal("countdown").executes(this::startCountdown)
        ).then(
            Commands.literal("ready").then(
                Commands.literal("players").executes(this::readyPlayers)
            ).then(
                Commands.literal("teams").executes(this::readyTeams)
            ).then(
                Commands.literal("awaiting").executes(this::awaitingReady)
            )
        )
    }

    private fun nextMinigameSettings(context: CommandContext<CommandSourceStack>): Int {
        val next = this.next ?: throw NO_MINIGAME.create()
        val player = context.source.playerOrException
        player.openMenu(next.createRulesMenu())
        return this.commandSuccess()
    }

    private fun setNextMinigame(context: CommandContext<CommandSourceStack>): Int {
        val minigame = MinigameArgument.getMinigame(context, "minigame")
        this.next = minigame
        return context.source.success("Successfully set the next minigame to ${minigame.id}")
    }

    private fun setNextNewMinigame(context: CommandContext<CommandSourceStack>): Int {
        val factory = MinigameArgument.Factory.getFactory(context, "minigame")
        val next = factory.create(context.source.server)
        this.next = next
        return context.source.success("Successfully set the next minigame to ${next.id}")
    }

    private fun unsetNextMinigame(context: CommandContext<CommandSourceStack>): Int {
        this.next = null
        return context.source.success("Successfully unset the next minigame")
    }

    private fun reloadLobby(context: CommandContext<CommandSourceStack>): Int {
        this.lobby.reload()
        return context.source.success("Successfully reloaded the lobby")
    }

    private fun deleteLobby(context: CommandContext<CommandSourceStack>): Int {
        this.lobby.area.removeBlocks()
        this.lobby.area.removeEntities { it !is Player }
        return context.source.success("Successfully removed the lobby")
    }

    private fun teleportToLobby(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        this.lobby.forceTeleportToSpawn(player)
        return context.source.success("Successfully teleported to the lobby")
    }

    private fun startCountdown(context: CommandContext<CommandSourceStack>): Int {
        this.next ?: return context.source.fail("Cannot move to next minigame, it has not been set!")
        this.setPhase(Phases.Countdown)
        return context.source.success("Successfully started the countdown")
    }

    private fun readyPlayers(context: CommandContext<CommandSourceStack>): Int {
        this.next ?: return context.source.fail("Cannot ready for next minigame, it has not been set!")
        val awaiting = this.arePlayersReady(this.getPlayersToReady())
        this.awaiting = {
            val component = "Awaiting the following players: ".literal()
            for (player in awaiting) {
                if (component.siblings.isNotEmpty()) {
                    component.append(", ")
                }
                component.append(player.displayName)
            }
            component
        }
        return context.source.success("Successfully broadcasted ready check")
    }

    private fun readyTeams(context: CommandContext<CommandSourceStack>): Int {
        this.next ?: return context.source.fail("Cannot ready for next minigame, it has not been set!")
        val awaiting = this.areTeamsReady(this.getTeamsToReady())
        this.awaiting = {
            val component = "Awaiting the following teams: ".literal()
            for (team in awaiting) {
                if (component.siblings.isNotEmpty()) {
                    component.append(", ")
                }
                component.append(team.formattedDisplayName)
            }
            component
        }
        return context.source.success("Successfully broadcasted ready check")
    }

    private fun awaitingReady(context: CommandContext<CommandSourceStack>): Int {
        val awaiting = this.awaiting ?: return context.source.fail("Not currently awaiting any players or teams to be ready")
        return context.source.success(awaiting())
    }

    private companion object {
        val NO_MINIGAME = SimpleCommandExceptionType("Lobby has no next minigame".literal())
    }

    public enum class Phases(override val id: String): MinigamePhase<LobbyMinigame> {
        Waiting("waiting") {
            override fun start(minigame: LobbyMinigame) {
                minigame.lobby.area.place()
                minigame.onStart()
            }
        },
        Countdown("countdown") {
            override fun start(minigame: LobbyMinigame) {
                val next = minigame.next
                if (next == null) {
                    Arcade.logger.warn("Tried counting down in lobby when there is no next minigame!")
                    minigame.setPhase(Waiting)
                    return
                }

                minigame.lobby.getCountdown().countdown(minigame).then {
                    minigame.setPhase(MinigamePhase.end())
                }
                minigame.onStartCountdown()
            }

            override fun end(minigame: LobbyMinigame) {
                minigame.moveToNextMinigame()
            }
        }
    }
}