package ir.carepack.feature.careplan

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ir.carepack.R
import ir.carepack.domain.careplan.CarePlanField
import ir.carepack.domain.careplan.CarePlanValidationError

@Composable
internal fun MedicationTextFields(
    medicationName: String,
    instruction: String,
    errors: Map<CarePlanField, String>,
    enabled: Boolean,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    instructionMinLines: Int,
    medicationNameTestTag: String? = null,
    instructionTestTag: String? = null,
) {
    OutlinedTextField(
        value = medicationName,
        onValueChange = onMedicationNameChanged,
        enabled = enabled,
        label = { Text(stringResource(R.string.medication_name_label)) },
        singleLine = true,
        isError = errors.containsKey(CarePlanField.MEDICATION_NAME),
        supportingText = { errors[CarePlanField.MEDICATION_NAME]?.let { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .optionalTestTag(medicationNameTestTag),
    )

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = instruction,
        onValueChange = onInstructionChanged,
        enabled = enabled,
        label = { Text(stringResource(R.string.instruction_label)) },
        minLines = instructionMinLines,
        isError = errors.containsKey(CarePlanField.INSTRUCTION),
        supportingText = { errors[CarePlanField.INSTRUCTION]?.let { Text(it) } },
        modifier = Modifier
            .fillMaxWidth()
            .optionalTestTag(instructionTestTag),
    )
}

internal fun List<CarePlanValidationError>.toFieldErrors(): Map<CarePlanField, String> =
    associate { error -> error.field to error.message }

private fun Modifier.optionalTestTag(testTag: String?): Modifier =
    if (testTag == null) this else this.testTag(testTag)
