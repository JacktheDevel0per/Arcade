package net.casual.arcade.minigame.managers

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.CommandNode
import net.casual.arcade.ducks.`Arcade$DeletableCommand`
import net.casual.arcade.events.minigame.*
import net.casual.arcade.events.player.PlayerCommandEvent
import net.casual.arcade.events.player.PlayerCommandSuggestionsEvent
import net.casual.arcade.events.player.PlayerSendCommandsEvent
import net.casual.arcade.minigame.Minigame
import net.casual.arcade.minigame.annotation.HAS_PLAYER
import net.casual.arcade.utils.CommandUtils.fail
import net.casual.arcade.utils.ComponentUtils.command
import net.casual.arcade.utils.ComponentUtils.grey
import net.casual.arcade.utils.ComponentUtils.hover
import net.casual.arcade.utils.ComponentUtils.italicise
import net.casual.arcade.utils.ComponentUtils.literal
import net.casual.arcade.utils.ComponentUtils.red
import net.casual.arcade.utils.ComponentUtils.underline
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentUtils
import net.minecraft.server.level.ServerPlayer
import java.util.*

public class MinigameCommandManager(
    private val minigame: Minigame<*>,
) {
    private val dispatcher = CommandDispatcher<CommandSourceStack>()
    private val registered = LinkedList<String>()

    init {
        this.minigame.events.register<PlayerSendCommandsEvent>(1_000, HAS_PLAYER) {
            it.addCustomCommandNode(this.dispatcher.root)
        }
        this.minigame.events.register<PlayerCommandEvent>(1_000, HAS_PLAYER) {
            this.onCommand(it)
        }
        this.minigame.events.register<PlayerCommandSuggestionsEvent>(1_000, HAS_PLAYER) {
            this.onCommandSuggestions(it)
        }
        this.minigame.events.register<MinigameCloseEvent> {
            this.unregisterAll()
        }

        // Resending the command tree...
        this.minigame.events.register<MinigameAddPlayerEvent> {
            this.resendCommandsTo(it.player)
        }
        this.minigame.events.register<MinigameRemovePlayerEvent> {
            this.resendCommandsTo(it.player)
        }
        this.minigame.events.register<MinigameAddAdminEvent> {
            this.resendCommandsTo(it.player)
        }
        this.minigame.events.register<MinigameRemoveAdminEvent> {
            this.resendCommandsTo(it.player)
        }
    }

    public fun register(literal: LiteralArgumentBuilder<CommandSourceStack>) {
        this.registered.add(literal.literal)
        this.dispatcher.register(literal)

        val global = this.getGlobalMinigameCommand() ?: return
        global.addChild(
            Commands.literal(this.minigame.uuid.toString()).then(literal).build()
        )
        this.resendGlobalCommands()
    }

    public fun unregister(name: String) {
        if (this.registered.remove(name)) {
            (this.dispatcher as `Arcade$DeletableCommand`).`arcade$delete`(name)
            val command = this.getGlobalMinigameCommand()?.getChild(this.minigame.uuid.toString()) ?: return
            (command as `Arcade$DeletableCommand`).`arcade$delete`(name)
            this.resendGlobalCommands()
        }
    }

    public fun unregisterAll() {
        val dispatcher = this.dispatcher as `Arcade$DeletableCommand`
        for (name in this.registered) {
            dispatcher.`arcade$delete`(name)
            val command = this.getGlobalMinigameCommand()?.getChild(this.minigame.uuid.toString()) ?: return
            (command as `Arcade$DeletableCommand`).`arcade$delete`(name)
        }
        this.registered.clear()
        this.resendGlobalCommands()
    }

    public fun getAllRootCommands(): Collection<String> {
        return this.registered
    }

    private fun resendCommands() {
        for (player in this.minigame.getAllPlayers()) {
            this.resendCommandsTo(player)
        }
    }

    private fun resendGlobalCommands() {
        for (player in this.minigame.server.playerList.players) {
            this.resendCommandsTo(player)
        }
    }

    private fun resendCommandsTo(player: ServerPlayer) {
        this.minigame.server.commands.sendCommands(player)
    }

    private fun getGlobalMinigameCommand(): CommandNode<CommandSourceStack>? {
        return null
        // FIXME:
        // return this.minigame.server.commands.dispatcher.root.getChild("minigame")?.getChild("command")
    }

    private fun onCommand(event: PlayerCommandEvent) {
        val source = event.player.createCommandSourceStack()
        val result = this.dispatcher.parse(event.command, source)
        if (!result.reader.canRead()) {
            try {
                this.dispatcher.execute(result)
            }  catch (syntax: CommandSyntaxException) {
                source.fail(ComponentUtils.fromMessage(syntax.rawMessage))
                if (syntax.input != null && syntax.cursor >= 0) {
                    val i = syntax.input.length.coerceAtMost(syntax.cursor);
                    val command = Component.empty().grey().command("/${event.command}")
                    if (i > 10) {
                        command.append(CommonComponents.ELLIPSIS)
                    }

                    command.append(syntax.input.substring(0.coerceAtLeast(i - 10), i))
                    if (i < syntax.input.length) {
                        val component = syntax.input.substring(i).literal().red().underline()
                        command.append(component)
                    }

                    command.append(Component.translatable("command.context.here").red().italicise())
                    source.sendFailure(command)
                }
            } catch (e: Exception) {
                source.fail("Command threw unexpected exception: ${e.message}".literal().hover(e.stackTraceToString()))
            }
            event.cancel()
        }
    }

    private fun onCommandSuggestions(event: PlayerCommandSuggestionsEvent) {
        val result = this.dispatcher.parse(event.createCommandReader(), event.player.createCommandSourceStack())
        event.addSuggestions(this.dispatcher.getCompletionSuggestions(result))
    }
}