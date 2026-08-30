package com.keavors.gallery.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The controls that sit over a photograph: the viewer's two bars, the video
 * controls and the editor.
 *
 * They share a look — white, over a dark scrim — and, more to the point, a
 * size. Anything here is competing with the picture for the screen, so the part
 * that is seen is kept small. That is only bearable if the part that answers a
 * thumb is not, and the two are not the same rectangle.
 */

/**
 * How large a control is to a finger, whatever its icon measures.
 *
 * Forty-eight dp is the floor the guidelines put under this, and a floor is what
 * it is: these are pressed one-handed, at arm's length, over a photograph that
 * gives no clue where the button behind it ends.
 */
val TOUCH_TARGET = 56.dp

/** Tall enough for an icon with a word under it, and for a thumb. */
private val LABELLED_HEIGHT = 66.dp

/**
 * An icon on its own, grown to [TOUCH_TARGET].
 *
 * The icon keeps whatever size it was drawn at; only the reach around it grows.
 */
@Composable
fun ChromeIconButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(TOUCH_TARGET),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * An icon with its name under it, as used in the bars along the bottom.
 *
 * The whole cell is the button, label and the air around it included, rather
 * than the icon alone with the word beneath it decorative and dead. Give it a
 * weight in the row and every action gets the same share of the width whatever
 * its name is as long as — which is what stops four buttons from sitting at four
 * unrelated distances from each other.
 *
 * [description] is what the action does, for anyone being read to; [label] is
 * only what fits under an icon.
 */
@Composable
fun BarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String = label,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = LABELLED_HEIGHT)
            .clip(RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = null,
                // A white ripple: the default takes its colour from the theme,
                // which over a black bar can be a ripple nobody can see.
                indication = ripple(color = Color.White),
                onClickLabel = description,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 2.dp, vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            // The label underneath is the name of this control; saying it twice
            // would have it read out twice.
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
