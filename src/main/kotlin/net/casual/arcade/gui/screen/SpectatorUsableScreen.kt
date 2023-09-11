package net.casual.arcade.gui.screen

import net.minecraft.world.inventory.AbstractContainerMenu

/**
 * This can be implemented by any [AbstractContainerMenu] implementation
 * and will allow players who are in spectator mode to use the menu.
 */
interface SpectatorUsableScreen {
    /**
     * Returns whether spectators can use this screen.
     *
     * @return Whether spectators can use the screen.
     */
    fun isSpectatorUsable(): Boolean {
        return true
    }
}