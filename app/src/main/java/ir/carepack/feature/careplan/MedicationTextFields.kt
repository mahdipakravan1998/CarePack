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
import ir.carepack.ui.accessibility.carePackPoliteLiveRegion

@Composable
internal fun MedicationTextFields(
    medicationName: String,
    instruction: String,
    medicationType: String,
    dosageText: String,
    doseUnit: String,
    errors: Map<CarePlanField, String>,
    enabled: Boolean,
    onMedicationNameChanged: (String) -> Unit,
    onInstructionChanged: (String) -> Unit,
    onMedicationTypeChanged: (String) -> Unit,
    onDosageTextChanged: (String) -> Unit,
    onDoseUnitChanged: (String) -> Unit,
    instructionMinLines: Int,
    medicationNameTestTag: String? = null,
    instructionTestTag: String? = null,
    medicationTypeTestTag: String? = null,
    dosageTextTestTag: String? = null,
    doseUnitTestTag: String? = null,
) {
    MedicationTextField(
        value = medicationName,
        onValueChange = onMedicationNameChanged,
        enabled = enabled,
        labelResId = R.string
                .medication_name_label,
        singleLine = true,
        minLines = 1,
        field = CarePlanField
                .MEDICATION_NAME,
        errors = errors,
        testTag = medicationNameTestTag,
    )

    Spacer(
        modifier = Modifier.height(
                12.dp,
            ),
    )

    MedicationTextField(
        value = instruction,
        onValueChange = onInstructionChanged,
        enabled = enabled,
        labelResId = R.string
                .instruction_label,
        singleLine = false,
        minLines = instructionMinLines,
        field = CarePlanField
                .INSTRUCTION,
        errors = errors,
        testTag = instructionTestTag,
    )

    Spacer(
        modifier = Modifier.height(
                12.dp,
            ),
    )

    MedicationTextField(
        value = medicationType,
        onValueChange = onMedicationTypeChanged,
        enabled = enabled,
        labelResId = R.string
                .medication_type_label,
        singleLine = true,
        minLines = 1,
        field = CarePlanField
                .MEDICATION_TYPE,
        errors = errors,
        testTag = medicationTypeTestTag,
    )

    Spacer(
        modifier = Modifier.height(
                12.dp,
            ),
    )

    MedicationTextField(
        value = dosageText,
        onValueChange = onDosageTextChanged,
        enabled = enabled,
        labelResId = R.string
                .dosage_text_label,
        singleLine = true,
        minLines = 1,
        field = CarePlanField
                .DOSAGE_TEXT,
        errors = errors,
        testTag = dosageTextTestTag,
    )

    Spacer(
        modifier = Modifier.height(
                12.dp,
            ),
    )

    MedicationTextField(
        value = doseUnit,
        onValueChange = onDoseUnitChanged,
        enabled = enabled,
        labelResId = R.string
                .dose_unit_label,
        singleLine = true,
        minLines = 1,
        field = CarePlanField
                .DOSE_UNIT,
        errors = errors,
        testTag = doseUnitTestTag,
    )
}

@Composable
private fun MedicationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    labelResId: Int,
    singleLine: Boolean,
    minLines: Int,
    field: CarePlanField,
    errors: Map<CarePlanField, String>,
    testTag: String?,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = {
            Text(
                text = stringResource(
                        labelResId,
                    ),
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        isError = errors.containsKey(
                field,
            ),
        supportingText = {
            errors[field]?.let {
                    errorMessage ->
                Text(
                    text = errorMessage,
                    modifier = Modifier
                            .carePackPoliteLiveRegion(),
                )
            }
        },
        modifier = Modifier
                .fillMaxWidth().optionalTestTag(
                    testTag,
                ),
    )
}

internal fun List<CarePlanValidationError>.toFieldErrors(): Map<CarePlanField, String> =
    associate {
            error ->
        error.field to
                error.message
    }

private fun Modifier.optionalTestTag(
    testTag: String?,
): Modifier = if (testTag == null) {
        this
    } else {
        testTag(testTag)
    }
