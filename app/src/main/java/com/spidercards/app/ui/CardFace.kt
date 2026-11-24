package com.spidercards.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spider.game.Card

object CardColors {
    val face = Color(0xFFFFFFFF)

    /** Face-up cards that can't be moved (not part of the run at the bottom of their column). */
    val locked = Color(0xFFCBCBCB)
    val edge = Color(0xFF8A8A8A)
    val black = Color(0xFF111111)
    val red = Color(0xFFE3141B)
    val back = Color(0xFFC8161D)
    val backDark = Color(0xFF9E0F15)
    val backLine = Color(0xFFE5474C)
    val slot = Color(0x2EFFFFFF)
    val slotEdge = Color(0x99FFFFFF)
}

private fun cardShape(width: Dp) = RoundedCornerShape(width * 0.08f)

/** Text sized from the card width rather than the system font scale, so it always fits the card. */
private fun cardTextStyle(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Medium) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    lineHeight = size,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
fun CardFace(
    card: Card,
    width: Dp,
    fontScale: Float,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    locked: Boolean = false,
) {
    val shape = cardShape(width)
    if (!card.faceUp) {
        CardBack(width, modifier)
        return
    }
    val color = if (card.suit.isRed) CardColors.red else CardColors.black
    // Sizes in sp derived from the card width, undoing the user's font scale so cards never overflow.
    val corner = (width.value * 0.40f / fontScale).sp
    val big = (width.value * 0.78f / fontScale).sp
    Box(
        modifier
            .clip(shape)
            .background(if (locked) CardColors.locked else CardColors.face)
            .border(
                if (highlighted) 2.dp else 0.75.dp,
                if (highlighted) Color(0xFFFFC107) else CardColors.edge,
                shape,
            ),
    ) {
        // Rank on the left, suit on the right, both large so they read on a narrow overlap.
        Row(
            Modifier.fillMaxWidth().padding(start = width * 0.07f, end = width * 0.07f, top = width * 0.05f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                card.rankLabel,
                style = cardTextStyle(corner, color).copy(letterSpacing = (-1).sp),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
            Text(card.suit.symbol, style = cardTextStyle(corner, color), maxLines = 1, softWrap = false)
        }
        Text(
            card.suit.symbol,
            style = cardTextStyle(big, color).copy(textAlign = TextAlign.Center),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * 0.10f),
        )
    }
}

/** A red back with a white border and a woven lattice, drawn in code. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val shape = cardShape(width)
    Canvas(modifier.clip(shape).background(Color.White).border(0.75.dp, CardColors.edge, shape)) {
        val inset = size.width * 0.07f
        val inner = Size(size.width - inset * 2, size.height - inset * 2)
        val radius = CornerRadius(size.width * 0.04f)
        drawRoundRect(CardColors.back, topLeft = Offset(inset, inset), size = inner, cornerRadius = radius)
        clipRect(inset, inset, size.width - inset, size.height - inset) {
            val step = size.width / 6f
            val stroke = size.width * 0.025f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(CardColors.backLine, Offset(x, 0f), Offset(x + size.height, size.height), stroke)
                drawLine(CardColors.backLine, Offset(x, size.height), Offset(x + size.height, 0f), stroke)
                x += step
            }
        }
        drawRoundRect(
            CardColors.backDark,
            topLeft = Offset(inset, inset),
            size = inner,
            cornerRadius = radius,
            style = Stroke(size.width * 0.03f),
        )
    }
}

/** The outline shown where a column, foundation or stock is empty. */
@Composable
fun EmptySlot(width: Dp, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val shape = cardShape(width)
    val edge = if (highlighted) Color(0xFFFFC107) else CardColors.slotEdge
    Box(modifier.clip(shape).background(CardColors.slot).border(if (highlighted) 3.dp else 1.5.dp, edge, shape)) {
        Box(Modifier.fillMaxSize())
    }
}
