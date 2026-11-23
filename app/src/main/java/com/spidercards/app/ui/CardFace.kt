package com.spidercards.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
    val face = Color(0xFFFDFCF7)
    val edge = Color(0xFF9E9E9E)
    val black = Color(0xFF1B1B1B)
    val red = Color(0xFFC62828)
    val backTop = Color(0xFF2B4C9B)
    val backBottom = Color(0xFF16306E)
    val backTrim = Color(0xFF8FA9E6)
    val slot = Color(0x33FFFFFF)
}

private fun cardShape(width: Dp) = RoundedCornerShape(width * 0.1f)

/** Text sized from the card width rather than the system font scale, so it always fits the card. */
@Composable
private fun cardTextStyle(size: TextUnit, color: Color) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = FontWeight.Bold,
    lineHeight = size,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)

@Composable
fun CardFace(card: Card, width: Dp, fontScale: Float, modifier: Modifier = Modifier, highlighted: Boolean = false) {
    val shape = cardShape(width)
    if (!card.faceUp) {
        Box(
            modifier
                .clip(shape)
                .background(Brush.verticalGradient(listOf(CardColors.backTop, CardColors.backBottom)))
                .border(1.dp, CardColors.backBottom, shape)
                .padding(width * 0.1f)
                .border(1.dp, CardColors.backTrim.copy(alpha = 0.6f), RoundedCornerShape(width * 0.06f)),
        )
        return
    }
    val color = if (card.suit.isRed) CardColors.red else CardColors.black
    // Sizes in sp derived from the card width, undoing the user's font scale so cards never overflow.
    val corner = (width.value * 0.36f / fontScale).sp
    val big = (width.value * 0.62f / fontScale).sp
    Box(
        modifier
            .clip(shape)
            .background(CardColors.face)
            .border(if (highlighted) 2.dp else 0.5.dp, if (highlighted) Color(0xFFFFC107) else CardColors.edge, shape),
    ) {
        Column(Modifier.padding(start = width * 0.06f, top = width * 0.04f)) {
            Text(card.rankLabel, style = cardTextStyle(corner, color), maxLines = 1, softWrap = false)
            Text(card.suit.symbol, style = cardTextStyle(corner * 0.9f, color), maxLines = 1)
        }
        Text(
            card.suit.symbol,
            style = cardTextStyle(big, color).copy(textAlign = TextAlign.Center),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = width * 0.12f),
        )
    }
}

/** The outline shown where a column, foundation or stock is empty. */
@Composable
fun EmptySlot(width: Dp, modifier: Modifier = Modifier, label: String? = null, fontScale: Float = 1f) {
    val shape = cardShape(width)
    Box(modifier.clip(shape).border(1.dp, CardColors.slot, shape).background(Color(0x14000000))) {
        if (label != null) {
            Text(
                label,
                style = cardTextStyle((width.value * 0.3f / fontScale).sp, CardColors.slot),
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(Modifier.fillMaxSize())
    }
}
