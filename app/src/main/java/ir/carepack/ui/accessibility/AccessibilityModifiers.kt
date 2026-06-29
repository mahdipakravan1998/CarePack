package ir.carepack.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

fun Modifier.carePackHeading(): Modifier =
    semantics {
        heading()
    }

fun Modifier.carePackPoliteLiveRegion(): Modifier =
    semantics {
        liveRegion =
            LiveRegionMode.Polite
    }
