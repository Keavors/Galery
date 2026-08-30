package com.keavors.gallery.ui.lock

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.keavors.gallery.R
import com.keavors.gallery.data.authenticate
import com.keavors.gallery.ui.common.opaqueToTouch

/**
 * Stands in front of whatever is locked.
 *
 * It asks once by itself on the way in, because being made to press a button
 * before being asked for a fingerprint is one step too many. If that is refused
 * or cancelled, the button is there to ask again — the alternative, prompting
 * over and over, is a screen nobody can leave.
 */
@Composable
fun LockScreen(
    title: String,
    subtitle: String,
    onUnlocked: () -> Unit,
    onGiveUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current ?: return
    val unlock by rememberUpdatedState(onUnlocked)
    var asking by remember { mutableStateOf(true) }

    BackHandler { onGiveUp() }

    LaunchedEffect(asking) {
        if (!asking) return@LaunchedEffect
        activity.authenticate(
            title = title,
            subtitle = subtitle,
            onSuccess = { unlock() },
            onCancelled = { asking = false },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .opaqueToTouch()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(46.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 18.dp),
        )
        if (!asking) {
            Button(
                onClick = { asking = true },
                modifier = Modifier.padding(top = 22.dp),
            ) {
                Text(stringResource(R.string.lock_unlock))
            }
        }
    }
}
