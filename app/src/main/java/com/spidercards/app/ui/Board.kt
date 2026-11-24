package com.spidercards.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import spider.game.Action
import spider.game.Card
import spider.game.GameState
import spider.game.Spider
import kotlin.math.roundToInt

private fun Offset.round() = IntOffset(x.roundToInt(), y.roundToInt())

/**
 * Where everything sits on screen, in pixels. Rebuilt whenever the state or screen size changes.
 *
 * Landscape keeps all the height for the tableau: completed runs and the stock go in a strip on the right.
 * Portrait puts them in a row along the bottom. In both, cards shrink as columns grow so that every
 * card's rank stays readable, and only if that isn't enough do the overlaps squeeze further.
 */
private class BoardLayout(
    val state: GameState,
    width: Float,
    height: Float,
) {
    val landscape = width > height
    val columnWidth = width / (GameState.COLUMN_COUNT + if (landscape) 1 else 0)
    val gap = columnWidth * 0.05f
    val maxCardWidth = columnWidth - gap * 2
    val top = gap * 2

    val cardWidth: Float
    val cardHeight: Float
    private val tableauBottom: Float

    init {
        // Room for the tableau, using the biggest card size to reserve the portrait bottom row.
        val available = if (landscape) height - top - gap else height - top - maxCardWidth * ASPECT - gap * 4
        // The tallest column in card widths, at tight but readable overlaps. Cards only shrink when even
        // that doesn't fit, and never below MIN_SCALE; past that the overlaps squeeze instead.
        val tallest = state.columns.maxOf { column ->
            ASPECT + column.dropLast(1).sumOf { (if (it.faceUp) FIT_UP else MIN_DOWN).toDouble() }.toFloat()
        }
        cardWidth = (available / tallest).coerceIn(maxCardWidth * MIN_SCALE, maxCardWidth)
        cardHeight = cardWidth * ASPECT
        tableauBottom = if (landscape) height - gap else height - cardHeight - gap * 3
    }

    /** Card tops for each column. */
    val columnY: List<List<Float>> = state.columns.map { stackY(it) }

    /** Card tops for a column of cards. Hidden cards are squeezed first, then visible ones if still needed. */
    fun stackY(column: List<Card>): List<Float> {
        val room = tableauBottom - top - cardHeight
        val hidden = column.dropLast(1).count { !it.faceUp }
        val shown = column.size - 1 - hidden
        var down = cardWidth * DOWN
        var up = cardWidth * UP
        if (hidden * down + shown * up > room) {
            down = cardWidth * MIN_DOWN
            if (hidden * down + shown * up > room) {
                up = if (shown > 0) ((room - hidden * down) / shown).coerceAtLeast(cardWidth * MIN_UP) else up
            } else {
                down = if (hidden > 0) ((room - shown * up) / hidden).coerceIn(cardWidth * MIN_DOWN, cardWidth * DOWN) else down
            }
        }
        var y = top
        return column.map { card ->
            val cardY = y
            y += if (card.faceUp) up else down
            cardY
        }
    }

    fun columnX(column: Int) = column * columnWidth + gap

    // Landscape: the right-hand strip. Portrait: the bottom row.
    private val sideX = GameState.COLUMN_COUNT * columnWidth + gap
    private val bottomRowY = height - cardHeight - gap

    private val stockStep = if (landscape) cardHeight * 0.12f else columnWidth * 0.3f

    fun foundationPosition(index: Int): Offset = if (landscape) {
        val stockTop = bottomRowY - (GameState.RUNS_TO_WIN / 2) * stockStep
        val step = ((stockTop - gap * 2 - cardHeight - top) / (GameState.RUNS_TO_WIN - 1))
            .coerceIn(cardHeight * 0.05f, cardHeight * 0.2f)
        Offset(sideX, top + index * step)
    } else {
        Offset(gap + index * columnWidth * 0.55f, bottomRowY)
    }

    /** The stock shows one small pile per deal left. */
    fun stockPosition(pile: Int, piles: Int): Offset {
        val back = (piles - 1 - pile).coerceAtLeast(0)
        return if (landscape) {
            Offset(sideX, bottomRowY - back * stockStep)
        } else {
            Offset(columnX(GameState.COLUMN_COUNT - 1) - back * stockStep, bottomRowY)
        }
    }

    fun isOnStock(point: Offset): Boolean {
        val first = stockPosition(0, maxOf(state.dealsLeft, 1))
        val last = stockPosition(0, 1)
        return point.x >= first.x && point.x <= last.x + cardWidth &&
            point.y >= first.y && point.y <= last.y + cardHeight
    }

    fun columnAt(x: Float): Int = (x / columnWidth).toInt().coerceIn(0, GameState.COLUMN_COUNT - 1)

    /** The card under [point] as (column, index), or null when the touch misses every card. */
    fun cardAt(point: Offset): Pair<Int, Int>? {
        if (point.x >= GameState.COLUMN_COUNT * columnWidth || point.y > tableauBottom) return null
        val column = columnAt(point.x)
        val ys = columnY[column]
        for (i in ys.indices.reversed()) {
            if (point.y >= ys[i] && point.y <= ys[i] + cardHeight) return column to i
        }
        return null
    }

    companion object {
        const val ASPECT = 1.4f

        // Overlaps, in card widths. UP leaves the rank and suit in the corner showing.
        const val UP = 0.50f
        const val DOWN = 0.08f
        const val MIN_DOWN = 0.035f

        /** Overlap used to decide the card size: the rank is still fully readable. */
        const val FIT_UP = 0.38f

        /** The tightest overlap ever used. The top of the rank still shows; holding the column shows it all. */
        const val MIN_UP = 0.22f

        /** Cards never shrink below this share of their full size, so they stay readable and easy to tap. */
        const val MIN_SCALE = 0.8f
    }
}

private data class Placement(
    val card: Card,
    val x: Float,
    val y: Float,
    val z: Float,
    val locked: Boolean = false,
    val column: Int = -1,
    val index: Int = -1,
)

private val cardSpring = spring<IntOffset>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMediumLow,
)
private const val DEAL_STEP_MS = 60L
private const val DEAL_MS = 200
private const val COLLECT_MS = 420
private const val PEEK_MS = 2500L

/** How a card should travel to its new spot, when it isn't a plain move. */
private sealed interface Flight {
    /** One of the ten cards of a deal; [order] is its column, so they go out one after another. */
    data class Deal(val order: Int) : Flight

    /** Part of a completed run: settle at [via] in the column, wait [waitMs], then fly to the foundation. */
    data class Collect(val via: IntOffset, val waitMs: Long) : Flight
}

/** Compares each new position with the last, to choreograph deals and completed runs. */
private class FlightPlanner {
    private var lastState: GameState? = null
    var flights: Map<Int, Flight> = emptyMap()
        private set

    fun update(state: GameState, layout: BoardLayout) {
        val last = lastState
        if (last === state) return
        lastState = state
        if (last == null || state.moves != last.moves + 1) {
            flights = emptyMap()
            return
        }
        flights = buildMap {
            if (last.stock.size - state.stock.size == GameState.COLUMN_COUNT) {
                val dealt = last.stock.takeLast(GameState.COLUMN_COUNT).map { it.id }.toSet()
                state.columns.forEachIndexed { c, column ->
                    column.lastOrNull()?.takeIf { it.id in dealt }?.let { put(it.id, Flight.Deal(c)) }
                }
            }
            for (run in state.foundations.drop(last.foundations.size)) {
                // The run finished in the column that held its King, or (if the King moved too) an empty one.
                val king = run.first().id
                // If the whole run was already in the King's column before, the King itself moved.
                val column = last.columns.indexOfFirst { col -> col.any { it.id == king } }
                    .takeIf { c -> c >= 0 && last.columns[c].size - last.columns[c].indexOfFirst { it.id == king } < 13 }
                    ?: state.columns.indices.firstOrNull { last.columns[it].isEmpty() && state.columns[it].isEmpty() }
                    ?: continue
                val base = state.columns[column]
                val ys = layout.stackY(base + run)
                run.forEachIndexed { j, card ->
                    val via = IntOffset(layout.columnX(column).roundToInt(), ys[base.size + j].roundToInt())
                    // Pause so the finished run is seen, then stream it away from the Ace up.
                    put(card.id, Flight.Collect(via, COLLECT_PAUSE_MS + (run.lastIndex - j) * COLLECT_STEP_MS))
                }
            }
        }
    }

    companion object {
        const val COLLECT_PAUSE_MS = 350L
        const val COLLECT_STEP_MS = 55L
    }
}

private data class Peek(val column: Int, val index: Int, val timed: Boolean)

private data class Drag(val column: Int, val index: Int, val delta: Offset)

@Composable
fun Board(
    state: GameState,
    onMove: (from: Int, index: Int, to: Int) -> Boolean,
    onTapCard: (column: Int, index: Int) -> Boolean,
    onDeal: () -> Unit,
    modifier: Modifier = Modifier,
    hint: Action? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val layout = remember(state, widthPx, heightPx) { BoardLayout(state, widthPx, heightPx) }
        val cardWidthDp = with(density) { layout.cardWidth.toDp() }
        val cardHeightDp = with(density) { layout.cardHeight.toDp() }
        val fontScale = density.fontScale

        var drag by remember { mutableStateOf<Drag?>(null) }
        var peek by remember { mutableStateOf<Peek?>(null) }
        val currentLayout by rememberUpdatedState(layout)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnTap by rememberUpdatedState(onTapCard)
        val currentOnDeal by rememberUpdatedState(onDeal)

        // Empty slots: under every column, each foundation, and the stock.
        for (c in 0 until GameState.COLUMN_COUNT) {
            EmptySlot(
                cardWidthDp,
                highlighted = hintMove?.to == c && state.columns[c].isEmpty(),
                modifier =
                Modifier.offset { IntOffset(layout.columnX(c).roundToInt(), layout.top.roundToInt()) }
                    .size(cardWidthDp, cardHeightDp),
            )
        }
        for (f in 0 until GameState.RUNS_TO_WIN) {
            EmptySlot(
                cardWidthDp,
                Modifier.offset { layout.foundationPosition(f).round() }
                    .size(cardWidthDp, cardHeightDp),
            )
        }
        if (state.stock.isEmpty()) {
            EmptySlot(
                cardWidthDp,
                Modifier.offset { layout.stockPosition(0, 1).round() }.size(cardWidthDp, cardHeightDp),
            )
        }

        // Work out where every one of the 104 cards belongs.
        val placements = buildList {
            state.foundations.forEachIndexed { f, run ->
                run.forEachIndexed { i, card ->
                    val at = layout.foundationPosition(f)
                    add(Placement(card.copy(faceUp = true), at.x, at.y, f * 20f + i))
                }
            }
            val piles = state.dealsLeft
            state.stock.forEachIndexed { i, card ->
                val pile = (i / GameState.COLUMN_COUNT).coerceAtMost(maxOf(piles - 1, 0))
                val at = layout.stockPosition(pile, piles)
                add(Placement(card.copy(faceUp = false), at.x, at.y, 200f + i))
            }
            state.columns.forEachIndexed { c, column ->
                // Face-up cards above the movable run at the bottom of a column are shaded grey.
                var runStart = column.size
                while (runStart > 0 && Spider.isMovableRun(column, runStart - 1)) runStart--
                column.forEachIndexed { i, card ->
                    add(Placement(card, layout.columnX(c), layout.columnY[c][i], 400f + i, i < runStart, c, i))
                }
            }
        }

        // Cards (and possibly an empty column) to outline for the current hint.
        val hintMove = (hint as? Action.MoveCards)?.move
        val hintedIds: Set<Int> = when (hint) {
            is Action.MoveCards -> buildSet {
                state.columns.getOrNull(hint.move.from)?.drop(hint.move.index)?.forEach { add(it.id) }
                state.columns.getOrNull(hint.move.to)?.lastOrNull()?.let { add(it.id) }
            }
            Action.Deal -> state.stock.takeLast(GameState.COLUMN_COUNT).map { it.id }.toSet()
            null -> emptySet()
        }

        val planner = remember { FlightPlanner() }
        planner.update(state, layout)

        val activeDrag = drag
        val draggedIds = activeDrag?.let { d ->
            state.columns.getOrNull(d.column)?.drop(d.index)?.map { it.id }?.toSet()
        } ?: emptySet()

        for (p in placements) {
            key(p.card.id) {
                val dragging = p.card.id in draggedIds
                val dx = if (dragging) activeDrag!!.delta.x else 0f
                val dy = if (dragging) activeDrag!!.delta.y else 0f
                val target = IntOffset((p.x + dx).roundToInt(), (p.y + dy).roundToInt())
                val flight = planner.flights[p.card.id]
                val anim = remember { Animatable(target, IntOffset.VectorConverter) }
                LaunchedEffect(target, flight) {
                    when (flight) {
                        is Flight.Deal -> {
                            delay(flight.order * DEAL_STEP_MS)
                            anim.animateTo(target, tween(DEAL_MS, easing = FastOutSlowInEasing))
                        }
                        is Flight.Collect -> {
                            anim.animateTo(flight.via, cardSpring)
                            delay(flight.waitMs)
                            anim.animateTo(target, tween(COLLECT_MS, easing = FastOutSlowInEasing))
                        }
                        null -> if (dragging) anim.snapTo(target) else anim.animateTo(target, cardSpring)
                    }
                }
                val moving = anim.value != target
                // A dealt card stays face down until it lands.
                val faceDownInFlight = flight is Flight.Deal && moving
                CardFace(
                    card = if (faceDownInFlight) p.card.copy(faceUp = false) else p.card,
                    locked = p.locked,
                    width = cardWidthDp,
                    fontScale = fontScale,
                    highlighted = dragging || p.card.id in hintedIds ||
                        (peek?.let { it.column == p.column && it.index == p.index } ?: false),
                    modifier = Modifier
                        .offset { anim.value }
                        .zIndex(if (dragging) 1000f + p.z else if (moving) 900f + p.z else p.z)
                        .size(cardWidthDp, cardHeightDp),
                )
            }
        }

        // Peek: the face-up cards of one column, spread out side by side at full size.
        val shownPeek = peek
        val peekCards = shownPeek?.let { pk ->
            state.columns.getOrNull(pk.column)?.withIndex()?.filter { it.value.faceUp }
        }.orEmpty()
        if (shownPeek != null && peekCards.isNotEmpty()) {
            val peekWidth = with(density) { layout.maxCardWidth.toDp() }
            val room = maxWidth - 40.dp - peekWidth
            val step = if (peekCards.size > 1) minOf(peekWidth * 0.62f, room / (peekCards.size - 1)) else 0.dp
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .zIndex(2500f)
                    .background(Color(0xE60B2E14), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                Box(Modifier.size(step * (peekCards.size - 1) + peekWidth, peekWidth * BoardLayout.ASPECT)) {
                    peekCards.forEachIndexed { k, iv ->
                        CardFace(
                            card = iv.value,
                            width = peekWidth,
                            fontScale = fontScale,
                            highlighted = iv.index == shownPeek.index,
                            modifier = Modifier.offset(x = step * k).size(peekWidth, peekWidth * BoardLayout.ASPECT),
                        )
                    }
                }
            }
        }
        LaunchedEffect(shownPeek) {
            if (shownPeek?.timed == true) {
                delay(PEEK_MS)
                peek = null
            }
        }

        // One touch layer over the whole table: taps auto-move or deal, drags move runs.
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(2000f)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            // A peek from holding a card lasts only while the finger is down.
                            if (peek?.timed == false) peek = null
                        },
                        onLongPress = { point ->
                            currentLayout.cardAt(point)?.let { (c, i) -> peek = Peek(c, i, timed = false) }
                        },
                        onTap = { point ->
                            val l = currentLayout
                            val hit = l.cardAt(point)
                            peek = null
                            when {
                                // A card with nowhere to go shows its column spread out for a moment instead.
                                hit != null -> if (!currentOnTap(hit.first, hit.second)) {
                                    peek = Peek(hit.first, hit.second, timed = true)
                                }
                                l.isOnStock(point) -> currentOnDeal()
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point ->
                            peek = null
                            val l = currentLayout
                            val hit = l.cardAt(point)
                            drag = if (hit != null && Spider.isMovableRun(l.state.columns[hit.first], hit.second)) {
                                Drag(hit.first, hit.second, Offset.Zero)
                            } else {
                                null
                            }
                        },
                        onDrag = { change, amount ->
                            val d = drag ?: return@detectDragGestures
                            change.consume()
                            drag = d.copy(delta = d.delta + amount)
                        },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            if (d != null) {
                                val l = currentLayout
                                val centerX = l.columnX(d.column) + d.delta.x + l.cardWidth / 2
                                val target = l.columnAt(centerX)
                                if (target != d.column) currentOnMove(d.column, d.index, target)
                            }
                        },
                        onDragCancel = { drag = null },
                    )
                },
        )
    }
}
