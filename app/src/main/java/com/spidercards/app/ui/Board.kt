package com.spidercards.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import spider.game.Card
import spider.game.GameState
import spider.game.Spider
import kotlin.math.roundToInt

/** Where everything sits on screen, in pixels. Rebuilt whenever the state or screen size changes. */
private class BoardLayout(
    val state: GameState,
    width: Float,
    height: Float,
) {
    val columnWidth = width / GameState.COLUMN_COUNT
    val gap = columnWidth * 0.05f
    val cardWidth = columnWidth - gap * 2
    val cardHeight = cardWidth * 1.42f
    val top = gap * 2

    // Completed runs (left) and the stock (right) sit along the bottom edge.
    val bottomY = height - cardHeight - gap * 2
    private val tableauHeight = bottomY - top - gap * 2

    /** Card tops for each column, squeezed so tall columns still fit above the bottom row. */
    val columnY: List<List<Float>> = state.columns.map { column ->
        var down = cardHeight * 0.11f
        var up = cardHeight * 0.30f
        val steps = column.dropLast(1).sumOf { (if (it.faceUp) up else down).toDouble() }.toFloat()
        if (column.isNotEmpty() && cardHeight + steps > tableauHeight && steps > 0f) {
            val scale = ((tableauHeight - cardHeight) / steps).coerceAtLeast(0.05f)
            down *= scale
            up *= scale
        }
        var y = top
        column.map { card ->
            val cardY = y
            y += if (card.faceUp) up else down
            cardY
        }
    }

    fun columnX(column: Int) = column * columnWidth + gap

    fun foundationX(index: Int) = gap + index * columnWidth * 0.55f

    /** The stock shows one small pile per deal left, fanned leftwards from the right edge. */
    fun stockX(pile: Int, piles: Int) = columnX(GameState.COLUMN_COUNT - 1) - (piles - 1 - pile) * columnWidth * 0.3f

    fun stockLeft() = stockX(0, maxOf(state.dealsLeft, 1))

    fun isOnStock(point: Offset) =
        point.y >= bottomY && point.x >= stockLeft() && point.x <= columnX(GameState.COLUMN_COUNT - 1) + cardWidth

    fun columnAt(x: Float): Int = (x / columnWidth).toInt().coerceIn(0, GameState.COLUMN_COUNT - 1)

    /** The card under [point] as (column, index), or null when the touch misses every card. */
    fun cardAt(point: Offset): Pair<Int, Int>? {
        if (point.y >= bottomY) return null
        val column = columnAt(point.x)
        val ys = columnY[column]
        for (i in ys.indices.reversed()) {
            if (point.y >= ys[i] && point.y <= ys[i] + cardHeight) return column to i
        }
        return null
    }
}

private data class Placement(val card: Card, val x: Float, val y: Float, val z: Float)

private data class Drag(val column: Int, val index: Int, val delta: Offset)

@Composable
fun Board(
    state: GameState,
    onMove: (from: Int, index: Int, to: Int) -> Boolean,
    onTapCard: (column: Int, index: Int) -> Unit,
    onDeal: () -> Unit,
    modifier: Modifier = Modifier,
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
        val currentLayout by rememberUpdatedState(layout)
        val currentOnMove by rememberUpdatedState(onMove)
        val currentOnTap by rememberUpdatedState(onTapCard)
        val currentOnDeal by rememberUpdatedState(onDeal)

        // Empty slots: under every column, each foundation, and the stock.
        for (c in 0 until GameState.COLUMN_COUNT) {
            EmptySlot(
                cardWidthDp,
                Modifier.offset { IntOffset(layout.columnX(c).roundToInt(), layout.top.roundToInt()) }
                    .size(cardWidthDp, cardHeightDp),
            )
        }
        for (f in 0 until GameState.RUNS_TO_WIN) {
            EmptySlot(
                cardWidthDp,
                Modifier.offset { IntOffset(layout.foundationX(f).roundToInt(), layout.bottomY.roundToInt()) }
                    .size(cardWidthDp, cardHeightDp),
            )
        }
        if (state.stock.isEmpty()) {
            EmptySlot(
                cardWidthDp,
                Modifier.offset {
                    IntOffset(layout.columnX(GameState.COLUMN_COUNT - 1).roundToInt(), layout.bottomY.roundToInt())
                }.size(cardWidthDp, cardHeightDp),
            )
        }

        // Work out where every one of the 104 cards belongs.
        val placements = buildList {
            state.foundations.forEachIndexed { f, run ->
                run.forEachIndexed { i, card ->
                    add(Placement(card.copy(faceUp = true), layout.foundationX(f), layout.bottomY, f * 20f + i))
                }
            }
            val piles = state.dealsLeft
            state.stock.forEachIndexed { i, card ->
                val pile = (i / GameState.COLUMN_COUNT).coerceAtMost(maxOf(piles - 1, 0))
                add(Placement(card.copy(faceUp = false), layout.stockX(pile, piles), layout.bottomY, 200f + i))
            }
            state.columns.forEachIndexed { c, column ->
                column.forEachIndexed { i, card ->
                    add(Placement(card, layout.columnX(c), layout.columnY[c][i], 400f + i))
                }
            }
        }

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
                val position by animateIntOffsetAsState(
                    targetValue = target,
                    animationSpec = if (dragging) snap() else spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    label = "card",
                )
                CardFace(
                    card = p.card,
                    width = cardWidthDp,
                    fontScale = fontScale,
                    highlighted = dragging,
                    modifier = Modifier
                        .offset { position }
                        .zIndex(if (dragging) 1000f + p.z else if (position != target) 900f + p.z else p.z)
                        .size(cardWidthDp, cardHeightDp),
                )
            }
        }

        // One touch layer over the whole table: taps auto-move or deal, drags move runs.
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(2000f)
                .pointerInput(Unit) {
                    detectTapGestures { point ->
                        val l = currentLayout
                        val hit = l.cardAt(point)
                        when {
                            hit != null -> currentOnTap(hit.first, hit.second)
                            l.isOnStock(point) -> currentOnDeal()
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { point ->
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
