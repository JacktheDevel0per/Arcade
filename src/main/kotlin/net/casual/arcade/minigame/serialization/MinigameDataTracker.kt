package net.casual.arcade.minigame.serialization

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.casual.arcade.minigame.Minigame
import net.casual.arcade.utils.JsonUtils.array
import net.casual.arcade.utils.JsonUtils.long
import net.casual.arcade.utils.JsonUtils.objects
import net.casual.arcade.utils.JsonUtils.string
import net.casual.arcade.utils.JsonUtils.uuid
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import java.util.*
import kotlin.jvm.optionals.getOrNull

public class MinigameDataTracker(
    private val minigame: Minigame<*>
) {
    private val players = HashMap<UUID, JsonObject>()
    private var startTime = 0L
    private var endTime = 0L

    public fun start() {
        this.startTime = System.currentTimeMillis()
    }

    public fun end() {
        this.endTime = System.currentTimeMillis()
    }

    public fun updatePlayer(player: ServerPlayer) {
        val json = JsonObject()
        json.addProperty("uuid", player.stringUUID)
        // json.add("stats", this.minigame.stats.serialize(player))
        val array = JsonArray()
        for (advancement in this.minigame.advancements.all()) {
            if (!player.advancements.getOrStartProgress(advancement).isDone) {
                continue
            }
            val display = advancement.value.display.getOrNull() ?: continue
            val data = JsonObject()
            val item = display.icon.item
            data.addProperty("id", advancement.id.toString())
            data.addProperty("item", BuiltInRegistries.ITEM.getKey(display.icon.item).toString())
            data.addProperty("title", display.title.string)
            array.add(data)
        }
        json.add("advancements", array)

        this.players[player.uuid] = json
    }

    public fun getAdvancements(player: ServerPlayer): List<AdvancementHolder> {
        val json = this.players[player.uuid] ?: return listOf()
        val list = ArrayList<AdvancementHolder>()
        for (data in json.array("advancements").objects()) {
            val id = ResourceLocation(data.string("id"))
            list.add(this.minigame.advancements.get(id) ?: continue)
        }
        return list
    }

    public fun toJson(): JsonObject {
        return this.serialize { uuid, json ->
            json.add("stats", this.minigame.stats.serialize(uuid))
        }
    }

    internal fun serialize(modifier: ((UUID, JsonObject) -> Unit)? = null): JsonObject {
        for (player in this.minigame.getAllPlayers()) {
            this.updatePlayer(player)
        }

        val json = JsonObject()
        json.addProperty("minigame_start_ms", this.startTime)
        json.addProperty("minigame_end_ms", this.endTime)
        json.addProperty("id", this.minigame.id.toString())
        json.addProperty("uuid", this.minigame.uuid.toString())
        val players = JsonArray()
        for ((uuid, data) in this.players) {
            val player = if (modifier != null) { data.deepCopy().also { modifier(uuid, it) } } else data
            players.add(player)
        }
        json.add("players", players)
        return json
    }

    internal fun deserialize(json: JsonObject) {
        this.startTime = json.long("minigame_start_ms")
        this.endTime = json.long("minigame_end_ms")

        for (player in json.array("players").objects()) {
            this.players[player.uuid("uuid")] = player
        }
    }
}