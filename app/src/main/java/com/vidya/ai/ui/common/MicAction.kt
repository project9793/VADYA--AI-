package com.vidya.ai.ui.common

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.vidya.ai.core.MicPermission

/** Returns a click handler that asks for the mic permission first if needed. */
@Composable
fun rememberMicAction(onDenied: () -> Unit, action: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val currentAction by rememberUpdatedState(action)
    val currentDenied by rememberUpdatedState(onDenied)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) currentAction() else currentDenied() }
    return {
        if (MicPermission.has(ctx)) currentAction()
        else launcher.launch(Manifest.permission.RECORD_AUDIO)
    }
}
