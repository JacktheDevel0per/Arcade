package net.casual.arcade.utils

import net.casual.arcade.commands.hidden.HiddenCommand
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.server.level.ServerPlayer
import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.function.Consumer

object ComponentUtils {
    @Experimental
    @JvmStatic
    fun MutableComponent.singleUseFunction(consumer: Consumer<ServerPlayer>) {
        this.function { context ->
            consumer.accept(context.player)
            context.remove()
        }
    }

    @Experimental
    @JvmStatic
    fun MutableComponent.function(consumer: Consumer<ServerPlayer>): MutableComponent {
        return this.function { consumer.accept(it.player) }
    }

    @Experimental
    @JvmStatic
    fun MutableComponent.function(command: HiddenCommand): MutableComponent {
        return this.command(CommandUtils.registerHiddenCommand(command))
    }

    @JvmStatic
    fun MutableComponent.command(command: String): MutableComponent {
        return this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.RUN_COMMAND, command)) }
    }

    @JvmStatic
    fun MutableComponent.link(link: String): MutableComponent {
        return this.withStyle { it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, link)) }
    }

    @JvmStatic
    fun MutableComponent.bold(): MutableComponent {
        return this.withStyle(ChatFormatting.BOLD)
    }

    @JvmStatic
    fun MutableComponent.unbold(): MutableComponent {
        return this.withStyle { it.withBold(false) }
    }

    @JvmStatic
    fun MutableComponent.italicise(): MutableComponent {
        return this.withStyle(ChatFormatting.ITALIC)
    }

    @JvmStatic
    fun MutableComponent.unitalicise(): MutableComponent {
        return this.withStyle { it.withItalic(false) }
    }

    @JvmStatic
    fun MutableComponent.red(): MutableComponent {
        return this.withStyle(ChatFormatting.RED)
    }

    @JvmStatic
    fun MutableComponent.crimson(): MutableComponent {
        return this.withStyle(ChatFormatting.DARK_RED)
    }

    @JvmStatic
    fun MutableComponent.navy(): MutableComponent {
        return this.withStyle(ChatFormatting.DARK_BLUE)
    }

    @JvmStatic
    fun MutableComponent.blue(): MutableComponent {
        return this.withStyle(ChatFormatting.BLUE)
    }

    @JvmStatic
    fun MutableComponent.aqua(): MutableComponent {
        return this.withStyle(ChatFormatting.AQUA)
    }

    @JvmStatic
    fun MutableComponent.teal(): MutableComponent {
        return this.withStyle(ChatFormatting.DARK_AQUA)
    }

    @JvmStatic
    fun MutableComponent.gold(): MutableComponent {
        return this.withStyle(ChatFormatting.GOLD)
    }

    @JvmStatic
    fun MutableComponent.pink(): MutableComponent {
        return this.withStyle(ChatFormatting.LIGHT_PURPLE)
    }

    @JvmStatic
    fun MutableComponent.purple(): MutableComponent {
        return this.withStyle(ChatFormatting.DARK_PURPLE)
    }

    @JvmStatic
    fun MutableComponent.lime(): MutableComponent {
        return this.withStyle(ChatFormatting.GREEN)
    }

    @JvmStatic
    fun MutableComponent.green(): MutableComponent {
        return this.withStyle(ChatFormatting.DARK_GREEN)
    }

    @JvmStatic
    fun MutableComponent.yellow(): MutableComponent {
        return this.withStyle(ChatFormatting.YELLOW)
    }

    @JvmStatic
    fun MutableComponent.white(): MutableComponent {
        return this.withStyle(ChatFormatting.WHITE)
    }

    @JvmStatic
    fun MutableComponent.colour(colour: Int): MutableComponent? {
        return this.withStyle { it.withColor(colour) }
    }
}