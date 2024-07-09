/*
 * MIT License
 *
 * Copyright (c) 2023 Samuel CHEMLA
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.phpbg.easysync.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.phpbg.easysync.ui.theme.EasySyncTheme

@Composable
fun Title(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun Description(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(lineBreak = LineBreak.Paragraph),
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
fun StdText(text: String, textAlign: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(lineBreak = LineBreak.Heading, color = MaterialTheme.colorScheme.onSurface),
        textAlign = textAlign,
    )
}

@Composable
fun PrimaryTextLarge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.primary),
    )
}

@Composable
fun RadioGroup(options: Map<String, String>, selected: String, onClick: (value: String) -> Unit) {
    options.forEach { option ->
        RadioChoice(
            text = option.value,
            selected = option.key == selected,
            onClick = {
                onClick(option.key)
            })
    }
}

@Composable
fun RadioChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        RadioButton(selected = selected, onClick = onClick)
        StdText(text = text)
    }
}

@Composable
fun SwitchSetting(
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SwitchSetting(title = null, description = description, checked = checked, onCheckedChange = onCheckedChange)
}

@Composable
fun SwitchSetting(
    title: String?,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1F)) {
            if (title != null) {
                StdText(title)
            }

            Description(text = description)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = false)
@Composable
private fun SwitchSettingPreview() {
    EasySyncTheme {
        SwitchSetting(
            title = "Foo",
            description = "Foo bar baz",
            checked = false,
            onCheckedChange = {})
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Composable
private fun SwitchSettingPreviewNoTitle() {
    EasySyncTheme {
        SwitchSetting(
            description = "Foo bar baz",
            checked = false,
            onCheckedChange = {})
    }
}
