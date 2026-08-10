package ir.carepack.feature.calendar

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.carepack.domain.calendar.PersianDateText
import java.time.DayOfWeek

@Composable
internal fun JalaliWeekdayHeader(weekdayOrder: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        weekdayOrder.forEach { dayOfWeek ->
            Text(
                text = PersianDateText.shortWeekdayName(dayOfWeek),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
            )
        }
    }
}
