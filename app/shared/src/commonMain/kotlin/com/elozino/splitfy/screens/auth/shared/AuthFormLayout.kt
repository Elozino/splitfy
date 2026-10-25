package com.elozino.splitfy.screens.auth.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elozino.splitfy.screens.common.FillOrScrollColumn
import com.elozino.splitfy.screens.common.rememberSideBySide

/**
 * An auth form under its heading. On a phone in landscape the heading stays on the left
 * and the form scrolls on the right, so the fields and the button aren't pushed below a
 * heading that takes half the screen. The heading gets a title size to suit: smaller in
 * the half-width column, so it wraps to two lines rather than three.
 */
@Composable
fun AuthFormLayout(
    modifier: Modifier,
    header: @Composable ColumnScope.(titleSize: TextUnit) -> Unit,
    form: @Composable ColumnScope.() -> Unit,
) {
    if (rememberSideBySide()) {
        Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 16.dp)) {
                header(26.sp)
            }
            FillOrScrollColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Spacer(modifier = Modifier.height(16.dp))
                form()
            }
        }
    } else {
        FillOrScrollColumn(modifier = modifier) {
            Spacer(modifier = Modifier.height(16.dp))
            header(32.sp)
            Spacer(modifier = Modifier.height(32.dp))
            form()
        }
    }
}
