package net.casual.arcade.minigame

import net.casual.arcade.events.EventHandler
import net.casual.arcade.events.GlobalEventHandler
import net.casual.arcade.events.core.Event
import net.casual.arcade.events.level.LevelEvent
import net.casual.arcade.events.minigame.*
import net.casual.arcade.events.player.PlayerEvent
import net.casual.arcade.events.server.ServerStoppedEvent
import net.casual.arcade.events.server.ServerTickEvent
import net.casual.arcade.gui.PlayerUI
import net.casual.arcade.gui.bossbar.CustomBossBar
import net.casual.arcade.gui.nametag.ArcadeNameTag
import net.casual.arcade.gui.screen.SelectionScreenComponents
import net.casual.arcade.gui.sidebar.ArcadeSidebar
import net.casual.arcade.gui.tab.ArcadeTabDisplay
import net.casual.arcade.scheduler.MinecraftTimeUnit
import net.casual.arcade.scheduler.Task
import net.casual.arcade.scheduler.TickedScheduler
import net.casual.arcade.settings.DisplayableGameSetting
import net.casual.arcade.settings.GameSetting
import net.casual.arcade.utils.EventUtils.registerHandler
import net.casual.arcade.utils.MinigameUtils.getMinigame
import net.casual.arcade.utils.MinigameUtils.minigame
import net.casual.arcade.utils.ScreenUtils
import net.casual.arcade.utils.ScreenUtils.DefaultMinigameScreenComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.ServerGamePacketListenerImpl
import net.minecraft.world.MenuProvider
import java.lang.StringBuilder
import java.util.*
import java.util.function.Consumer
import kotlin.collections.ArrayDeque
import kotlin.collections.set

/**
 * This class represents a [Minigame] which player's can play.
 * This is the superclass of all minigames.
 *
 * This implements the bare-bones logic for a minigame and
 * has common utilities used in minigames.
 *
 * Each minigame has its own set of [GameSetting]s,
 * has its own [EventHandler], and own [TickedScheduler].
 * Minigames also provide a way to display the UI to all
 * the currently playing players, through [CustomBossBar]s,
 * [ArcadeSidebar], [ArcadeTabDisplay], and [ArcadeNameTag]s.
 *
 * The minigame keeps track of who is currently playing,
 * this can be accessed through [getPlayers].
 * It also keeps track of the [ServerLevel]s which are
 * part of the minigame.
 *
 * As well as the minigames own state, see: [phase], [paused].
 *
 * You can implement your own minigame by extending this class:
 * ```kotlin
 * enum class MyMinigamePhases(
 *     override val id: String
 * ): MinigamePhase {
 *     Grace("grace"),
 *     Active("active"),
 *     DeathMatch("death_match")
 * }
 *
 * class MyMinigame(
 *     server: MinecraftServer
 * ): Minigame(ResourceLocation("modid", "my_minigame"), server) {
 *     val settings = Settings()
 *
 *     init {
 *         this.initialise()
 *     }
 *
 *     override fun getPhases(): Collection<MinigamePhase> {
 *         return MyMinigamePhases.values().toList()
 *     }
 *
 *     override fun getLevels(): Collection<ServerLevel> {
 *         return listOf(LevelUtils.overworld())
 *     }
 *
 *     private fun registerEvents() {
 *         this.registerMinigameEvent<MinigameAddPlayerEvent> { (_, player) ->
 *             player.sendSystemMessage(Component.literal("Welcome to My Minigame!"))
 *         }
 *     }
 *
 *     inner class Settings {
 *         val mySetting by registerSetting(
 *             DisplayableGameSettingBuilder.boolean()
 *                 .name("my_setting")
 *                 .display(ItemStack(Items.GLOWSTONE).literalNamed("My Setting"))
 *                 .defaultOptions()
 *                 .value(true)
 *                 .build()
 *         )
 *     }
 * }
 * ```
 *
 * @param id The [ResourceLocation] of the [Minigame].
 * @param server The [MinecraftServer] that created the [Minigame].
 *
 * @see SavableMinigame
 */
abstract class Minigame(
    /**
     * The [ResourceLocation] of the [Minigame].
     */
    val id: ResourceLocation,
    /**
     * The [MinecraftServer] that created the [Minigame].
     */
    val server: MinecraftServer,
) {
    private val connections = HashSet<ServerGamePacketListenerImpl>()
    private val events = EventHandler()
    private val phaseEvents = EventHandler()

    private val bossbars = ArrayList<CustomBossBar>()
    private val nameTags = ArrayList<ArcadeNameTag>()

    private var sidebar: ArcadeSidebar? = null
    private var display: ArcadeTabDisplay? = null
    private var closed = false

    internal val gameSettings = LinkedHashMap<String, DisplayableGameSetting<*>>()
    internal val scheduler = TickedScheduler()
    internal val phaseScheduler = TickedScheduler()
    @Deprecated("This will no longer be supported in future versions")
    internal val phaseEndTasks = ArrayDeque<Task>()
    internal val phases = LinkedHashSet(this.getPhases())

    internal var uuid = UUID.randomUUID()

    /**
     * What phase the minigame is currently in.
     * This, for example, may differ from whether there is a
     * grace period, death match, etc.
     *
     * @see setPhase
     * @see isPhase
     */
    var phase = MinigamePhase.NONE
        internal set

    /**
     * When minigames are paused, none of the scheduled
     * tasks will execute until the minigame in unpaused.
     *
     * @see pause
     * @see unpause
     */
    var paused = false
        internal set

    /**
     * This method initializes the core functionality of the
     * minigame, such as registering events.
     * This method should be called in your implementation's
     * constructor.
     */
    protected open fun initialise() {
        this.registerEvents()
        this.events.registerHandler()
        this.phaseEvents.registerHandler()

        Minigames.register(this)
    }

    /**
     * Checks whether the minigame is in a given phase.
     *
     * @param phase The phase to check whether the minigame is in.
     * @return Whether the minigame is in that phase.
     */
    fun isPhase(phase: MinigamePhase): Boolean {
        return this.phase == phase
    }

    /**
     * Checks whether the minigame is before a given phase.
     *
     * @param phase The phase to check whether the minigame is before.
     * @return Whether the minigame is before that phase.
     */
    fun isBeforePhase(phase: MinigamePhase): Boolean {
        return this.phase < phase
    }

    /**
     * Checks whether the minigame is past a given phase.
     *
     * @param phase The phase to check whether the minigame has past.
     * @return Whether the minigame is past that phase.
     */
    fun isPastPhase(phase: MinigamePhase): Boolean {
        return this.phase > phase
    }

    /**
     * This sets the phase of the minigame.
     * It will only be set if the given phase is
     * **different** to the current phase and in
     * the [phases] set.
     *
     * When a phase is set, all previously scheduled
     * phase tasks will be cleared and will no longer run.
     * Further, all the registered phase events will be
     * cleared and will no longer be invoked.
     *
     * After this the [MinigameSetPhaseEvent] is
     * broadcasted for listeners.
     *
     * @param phase The phase to set the minigame to.
     * @throws IllegalArgumentException If the [phase] is not in the [phases] set.
     */
    protected fun setPhase(phase: MinigamePhase) {
        if (this.isPhase(phase)) {
            return
        }
        if (!this.phases.contains(phase)) {
            throw IllegalArgumentException("Cannot set minigame '${this.id}' phase to ${phase.id}")
        }
        this.phaseScheduler.tasks.clear()

        for (task in this.phaseEndTasks) {
            task.run()
        }
        this.phaseEndTasks.clear()
        this.phaseEvents.clear()

        this.phase = phase
        this.phase.end(this)
        this.phase.start(this)
        this.phase.initialise(this)

        GlobalEventHandler.broadcast(MinigameSetPhaseEvent(this, phase))
    }

    /**
     * This adds a player to the minigame.
     * The player may be rejected from joining the minigame.
     * The minigame must not be [closed], and must not yet
     * be tracking the given player.
     * The player may also be rejected by the [MinigameAddNewPlayerEvent].
     *
     * If the player is accepted this method will return `true`.
     *
     * If the player previously logged out (or the server restarted),
     * the player will automatically rejoin the minigame and the
     * [MinigameAddExistingPlayerEvent] will be broadcast instead.
     *
     * In both cases of the player joining a singular other event
     * will be broadcasted [MinigameAddPlayerEvent].
     *
     * @param player The player to add to the minigame.
     * @return Whether the player was successfully accepted.
     */
    fun addPlayer(player: ServerPlayer): Boolean {
        if (this.closed || this.hasPlayer(player)) {
            return false
        }
        if (player.getMinigame() === this) {
            this.connections.add(player.connection)
            GlobalEventHandler.broadcast(MinigameAddExistingPlayerEvent(this, player))
            GlobalEventHandler.broadcast(MinigameAddPlayerEvent(this, player))
            return true
        }

        val event = MinigameAddNewPlayerEvent(this, player)
        GlobalEventHandler.broadcast(event)
        if (!event.isCancelled()) {
            this.connections.add(player.connection)
            player.minigame.setMinigame(this)
            GlobalEventHandler.broadcast(MinigameAddPlayerEvent(this, player))
            return true
        }
        return false
    }

    /**
     * This removes a given player from the minigame.
     * This also removes the player from all the minigame UI.
     *
     * If successful then the [MinigameRemovePlayerEvent] is
     * broadcast.
     *
     * @param player The player to remove.
     */
    fun removePlayer(player: ServerPlayer) {
        if (this.connections.remove(player.connection)) {
            GlobalEventHandler.broadcast(MinigameRemovePlayerEvent(this, player))
            player.minigame.removeMinigame()
        }
    }

    /**
     * This gets all the tracked players who are
     * playing this minigame.
     *
     * @return All the playing players.
     */
    fun getPlayers(): List<ServerPlayer> {
        return this.connections.map { it.player }
    }

    /**
     * This gets whether a given player is playing in the minigame.
     *
     * @param player The player to check whether they are playing.
     * @return Whether the player is playing.
     */
    fun hasPlayer(player: ServerPlayer): Boolean {
        return this.connections.contains(player.connection)
    }

    /**
     * This checks whether a given level is part of this minigame.
     *
     * @param level The level to check whether is part of the minigame.
     * @return Whether the level is part of the minigame.
     */
    fun hasLevel(level: ServerLevel): Boolean {
        return this.getLevels().contains(level)
    }

    /**
     * This gets all the registered [GameSetting]s for
     * this minigame.
     *
     * @return A collection of all the settings.
     */
    fun getSettings(): Collection<GameSetting<*>> {
        return this.gameSettings.values.map { it.setting }
    }

    /**
     * This gets a setting for a given name.
     *
     * @param name The name of the given setting.
     * @return The setting, may be null if non-existent.
     */
    fun getSetting(name: String): GameSetting<*>? {
        return this.gameSettings[name]?.setting
    }

    /**
     * This creates a [MenuProvider] which provides a GUI
     * for updating the minigame's [GameSetting]s.
     *
     * @param components The screen components to use for the GUI, by default [DefaultMinigameScreenComponent].
     * @return The [MenuProvider] for the settings screen.
     */
    open fun createRulesMenu(components: SelectionScreenComponents = DefaultMinigameScreenComponent): MenuProvider {
        return ScreenUtils.createMinigameRulesMenu(this, components)
    }

    /**
     * This will pause the minigame, stopping the scheduler
     * from executing any more tasks.
     * This will also broadcast the [MinigamePauseEvent].
     *
     * @see paused
     */
    fun pause() {
        this.paused = true
        GlobalEventHandler.broadcast(MinigamePauseEvent(this))
    }

    /**
     * This will unpause the minigame, resuming the scheduler.
     * This will also broadcast the [MinigameUnpauseEvent].
     *
     * @see paused
     */
    fun unpause() {
        this.paused = false
        GlobalEventHandler.broadcast(MinigameUnpauseEvent(this))
    }

    /**
     * This closes the minigame, all players are removed from the
     * minigame, all tasks are cleared, and all events are unregistered.
     *
     * This also broadcasts the [MinigameCloseEvent] after all the players
     * have been removed.
     *
     * After a minigame has been closed, no more players are permitted to join.
     */
    fun close() {
        for (player in this.getPlayers()) {
            this.removePlayer(player)
        }
        GlobalEventHandler.broadcast(MinigameCloseEvent(this))
        GlobalEventHandler.removeHandler(this.events)
        GlobalEventHandler.removeHandler(this.phaseEvents)
        this.scheduler.tasks.clear()
        this.phaseScheduler.tasks.clear()
        this.phaseEndTasks.clear()
        this.closed = true
    }

    fun addBossbar(bar: CustomBossBar) {
        this.bossbars.add(bar)
        this.loadUI(bar)
    }

    fun removeBossbar(bar: CustomBossBar) {
        this.bossbars.remove(bar)
        bar.clearPlayers()
    }

    fun removeBossbars() {
        for (bossbar in this.bossbars) {
            bossbar.clearPlayers()
        }
        this.bossbars.clear()
    }

    fun addNameTag(tag: ArcadeNameTag) {
        this.nameTags.add(tag)
        this.loadUI(tag)
    }

    fun removeNameTag(tag: ArcadeNameTag) {
        this.nameTags.remove(tag)
        tag.clearPlayers()
    }

    fun removeNameTags() {
        for (tag in this.nameTags) {
            tag.clearPlayers()
        }
        this.nameTags.clear()
    }

    fun setSidebar(sidebar: ArcadeSidebar) {
        this.removeSidebar()
        this.sidebar = sidebar
        this.loadUI(sidebar)
    }

    fun removeSidebar() {
        this.sidebar?.clearPlayers()
        this.sidebar = null
    }

    fun setTabDisplay(display: ArcadeTabDisplay) {
        this.removeTabDisplay()
        this.display = display
        this.loadUI(display)
    }

    fun removeTabDisplay() {
        this.display?.clearPlayers()
        this.display = null
    }

    private fun loadUI(ui: PlayerUI) {
        for (player in this.getPlayers()) {
            ui.addPlayer(player)
        }
    }

    final override fun toString(): String {
        val builder = StringBuilder()
        builder.append("~~~~~~~~~~~~~~").append("\n")
        builder.append("Minigame: ").append(this::class.java.simpleName).append("\n")
        builder.append("Serializable: ").append(this is SavableMinigame).append("\n")
        builder.append("UUID: ").append(this.uuid).append("\n")
        builder.append("ID: ").append(this.id).append("\n")
        builder.append("Players: ").append(this.getPlayers().joinToString("\n", "\n") { "  ${it.scoreboardName}" }).append("\n")
        builder.append("Levels: ").append(this.getLevels().joinToString("\n", "\n") { "  ${it.dimension().location()}" }).append("\n")
        builder.append("Phases: ").append(this.phases.joinToString("\n", "\n") { "  ${it.id}" }).append("\n")
        builder.append("Phase: ").append(this.phase.id).append("\n")
        builder.append("Paused: ").append(this.paused).append("\n")
        builder.append("Closed: ").append(this.closed).append("\n")
        builder.append("BossBars: ").append(this.bossbars.size).append("\n")
        builder.append("NameTags: ").append(this.nameTags.size).append("\n")
        builder.append("HasSidebar: ").append(this.sidebar != null).append("\n")
        builder.append("HasDisplay: ").append(this.display != null).append("\n")
        builder.append("Settings: ").append(this.getSettings().joinToString("\n", "\n") { "  ${it.name}: ${it.get()}" }).append("\n")
        builder.append("~~~~~~~~~~~~~~")
        return builder.toString()
    }

    /**
     * This gets all the [MinigamePhase]s that this [Minigame]
     * allows.
     *
     * This method will only be invoked **once**; when the
     * minigame is initialized, the phases are then stored in
     * a collection for the rest of the minigames lifetime.
     *
     * @return A collection of all the valid phases the minigame can be in.
     */
    protected abstract fun getPhases(): Collection<MinigamePhase>

    /**
     * This gets all the [ServerLevel]s that the [Minigame] is in.
     *
     * This method is used for [hasLevel], to see if the minigame
     * has a given level, and further for debugging purposes.
     *
     * @return A collection of levels that the minigame is in.
     */
    protected abstract fun getLevels(): Collection<ServerLevel>

    protected fun <T: Any> registerSetting(displayed: DisplayableGameSetting<T>): GameSetting<T> {
        val setting = displayed.setting
        this.gameSettings[setting.name] = displayed
        return setting
    }

    @Deprecated("Use MinigamePhase.end()")
    fun schedulePhaseEndTask(task: Task) {
        this.phaseEndTasks.add(task)
    }

    @Deprecated("Use MinigamePhase.end()")
    fun schedulePhaseEndTask(runnable: Runnable) {
        this.phaseEndTasks.add(Task.of(runnable))
    }

    fun scheduleTask(time: Int, unit: MinecraftTimeUnit, task: Task) {
        this.scheduler.schedule(time, unit, task)
    }

    fun scheduleTask(time: Int, unit: MinecraftTimeUnit, runnable: Runnable) {
        this.scheduler.schedule(time, unit, runnable)
    }

    fun scheduleInLoopTask(delay: Int, interval: Int, duration: Int, unit: MinecraftTimeUnit, runnable: Runnable) {
        this.scheduler.scheduleInLoop(delay, interval, duration, unit, runnable)
    }

    fun schedulePhaseTask(time: Int, unit: MinecraftTimeUnit, task: Task) {
        this.phaseScheduler.schedule(time, unit, task)
    }

    fun schedulePhaseTask(time: Int, unit: MinecraftTimeUnit, runnable: Runnable) {
        this.phaseScheduler.schedule(time, unit, runnable)
    }

    fun scheduleInLoopPhaseTask(delay: Int, interval: Int, duration: Int, unit: MinecraftTimeUnit, runnable: Runnable) {
        this.phaseScheduler.scheduleInLoop(delay, interval, duration, unit, runnable)
    }

    inline fun <reified T: Event> registerPhaseEvent(priority: Int = 1_000, listener: Consumer<T>) {
        this.registerPhaseEvent(T::class.java, priority, listener)
    }

    fun <T: Event> registerPhaseEvent(type: Class<T>, priority: Int = 1_000, listener: Consumer<T>) {
        this.phaseEvents.register(type, priority, listener)
    }

    inline fun <reified T: Event> registerEvent(priority: Int = 1_000, listener: Consumer<T>) {
        this.registerEvent(T::class.java, priority, listener)
    }

    fun <T: Event> registerEvent(type: Class<T>, priority: Int = 1_000, listener: Consumer<T>) {
        this.events.register(type, priority, listener)
    }

    inline fun <reified T: Event> registerMinigameEvent(priority: Int = 1_000, listener: Consumer<T>) {
        this.registerMinigameEvent(T::class.java, priority, listener)
    }

    fun <T: Event> registerMinigameEvent(type: Class<T>, priority: Int = 1_000, listener: Consumer<T>) {
        this.registerMinigameEvent(type, priority, listener, this.events)
    }

    inline fun <reified T: Event> registerPhaseMinigameEvent(priority: Int = 1_000, listener: Consumer<T>) {
        this.registerPhaseMinigameEvent(T::class.java, priority, listener)
    }

    fun <T: Event> registerPhaseMinigameEvent(type: Class<T>, priority: Int = 1_000, listener: Consumer<T>) {
        this.registerMinigameEvent(type, priority, listener, this.phaseEvents)
    }

    private fun <T: Event> registerMinigameEvent(type: Class<T>, priority: Int, listener: Consumer<T>, handler: EventHandler) {
        val predicates = LinkedList<(T) -> Boolean>()
        if (PlayerEvent::class.java.isAssignableFrom(type)) {
            predicates.add { this.hasPlayer((it as PlayerEvent).player) }
        }
        if (LevelEvent::class.java.isAssignableFrom(type)) {
            predicates.add { this.hasLevel((it as LevelEvent).level) }
        }
        if (MinigameEvent::class.java.isAssignableFrom(type)) {
            predicates.add { (it as MinigameEvent).minigame === this }
        }
        if (predicates.isEmpty()) {
            handler.register(type, priority, listener)
        } else {
            handler.register(type, priority) { event ->
                if (predicates.all { it(event) }) {
                    listener.accept(event)
                }
            }
        }
    }

    private fun registerEvents() {
        this.registerEvent<ServerTickEvent> { this.onServerTick() }
        this.registerEvent<ServerStoppedEvent> { this.close() }
        this.registerMinigameEvent<MinigameAddPlayerEvent> { (_, player) -> this.onPlayerAdd(player) }
        this.registerMinigameEvent<MinigameRemovePlayerEvent> { (_, player) -> this.onPlayerRemove(player) }
    }

    private fun onServerTick() {
        if (!this.paused) {
            this.scheduler.tick()
            this.phaseScheduler.tick()
        }
    }

    private fun onPlayerAdd(player: ServerPlayer) {
        this.bossbars.forEach { it.addPlayer(player) }
        this.nameTags.forEach { it.addPlayer(player) }
        this.sidebar?.addPlayer(player)
        this.display?.addPlayer(player)
    }

    private fun onPlayerRemove(player: ServerPlayer) {
        this.nameTags.forEach { it.removePlayer(player) }
        this.bossbars.forEach { it.removePlayer(player) }
        this.sidebar?.removePlayer(player)
        this.display?.removePlayer(player)
    }
}