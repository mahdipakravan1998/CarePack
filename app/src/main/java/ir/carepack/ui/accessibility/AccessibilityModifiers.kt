package ir.carepack.ui.accessibility

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import ir.carepack.ui.experience.LocalCarePackExperience

fun Modifier.carePackHeading(): Modifier = semantics {
        heading()
    }

fun Modifier.carePackPoliteLiveRegion(): Modifier = semantics {
        liveRegion = LiveRegionMode.Polite
    }

fun Modifier.carePackTraversalGroup(): Modifier = semantics {
        isTraversalGroup = true
    }

fun Modifier.carePackStateDescription(
    description: String,
): Modifier = semantics {
        stateDescription = description
    }

@Composable
fun Modifier.carePackPrimaryAction(): Modifier = sizeIn(
        minHeight = LocalCarePackExperience
                .current.primaryActionMinHeight,
    )

@Composable
fun Modifier.carePackInteractiveControl(): Modifier = sizeIn(
        minHeight = LocalCarePackExperience
                .current.controlMinHeight,
    )
