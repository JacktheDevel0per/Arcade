package net.casual.arcade.minigame.managers

import net.casual.arcade.Arcade
import net.casual.arcade.events.minigame.MinigameCloseEvent
import net.casual.arcade.events.server.ServerRecipeReloadEvent
import net.casual.arcade.minigame.Minigame
import net.casual.arcade.recipes.PlayerPredicatedRecipe
import net.casual.arcade.recipes.WrappedRecipe
import net.casual.arcade.utils.RecipeUtils.addRecipes
import net.casual.arcade.utils.RecipeUtils.removeRecipes
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Container
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.crafting.*

public class MinigameRecipeManager(
    private val minigame: Minigame<*>
) {
    private val recipes = ArrayList<RecipeHolder<*>>()

    init {
        this.minigame.events.register<ServerRecipeReloadEvent> {
            it.addAll(this.recipes)
        }
        this.minigame.events.register<MinigameCloseEvent> {
            this.removeAll()
        }
    }

    public fun add(recipes: Collection<RecipeHolder<*>>) {
        val minigameRecipes = recipes.map { this.wrap(it) }
        if (this.recipes.addAll(minigameRecipes)) {
            this.minigame.server.recipeManager.addRecipes(minigameRecipes)
        }
    }

    public fun remove(recipes: Collection<RecipeHolder<*>>) {
        if (this.recipes.removeAll(recipes.toSet())) {
            this.minigame.server.recipeManager.removeRecipes(recipes)
        }
    }

    public fun removeAll() {
        this.minigame.server.recipeManager.removeRecipes(this.recipes)
        this.recipes.clear()
    }

    public fun all(): Collection<RecipeHolder<*>> {
        return this.recipes
    }

    private fun wrap(holder: RecipeHolder<*>): RecipeHolder<*> {
        val wrapper = when (val recipe = holder.value) {
            is CraftingRecipe -> {
                object: MinigameRecipe<CraftingContainer>(recipe), CraftingRecipe {
                    override fun getType(): RecipeType<*> {
                        return RecipeType.CRAFTING
                    }

                    override fun category(): CraftingBookCategory {
                        return recipe.category()
                    }
                }
            }
            else -> MinigameRecipe(recipe)
        }
        return RecipeHolder(
            Arcade.id("${this.minigame.uuid}.${holder.id.path}.${holder.id.namespace}"),
            wrapper
        )
    }

    private open inner class MinigameRecipe<C: Container>(
        wrapped: Recipe<C>
    ): WrappedRecipe<C>(wrapped), PlayerPredicatedRecipe {
        override fun canUse(player: ServerPlayer): Boolean {
            return minigame.hasPlayer(player)
        }
    }
}