package io.github.kmpbits.composure.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kmpbits.composure.FieldState
import io.github.kmpbits.composure.FieldType
import io.github.kmpbits.composure.KeyboardHint

/**
 * Reference implementation of a Composure-aware Material3 text field.
 *
 * Reads [FieldState.type] to configure keyboard type and password masking
 * automatically. Copy and adapt this to your own design system — the library
 * itself has no UI dependency.
 *
 * ## Wiring up without this composable
 * ```kotlin
 * OutlinedTextField(
 *     value = field.value,
 *     onValueChange = { field.onChange(it) },
 *     modifier = Modifier.onFocusChanged { if (!it.isFocused) field.onBlur() },
 *     isError = field.isTouched && field.hasError,
 *     supportingText = { field.error?.let { Text(it) } },
 *     keyboardOptions = KeyboardOptions(
 *         keyboardType = field.type.keyboardHint.toComposeKeyboardType()
 *     ),
 *     visualTransformation = if (field.type.isSecret) PasswordVisualTransformation()
 *                            else VisualTransformation.None,
 * )
 * ```
 */
@Composable
fun ComposureTextField(
    field: FieldState<out FieldType>,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    imeAction: ImeAction = ImeAction.Next,
    enabled: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val value by field.value.collectAsState()
    val error by field.error.collectAsState()
    val isTouched by field.isTouched.collectAsState()
    val isDirty by field.isDirty.collectAsState()
    val isValidating by field.isValidating.collectAsState()

    val showError = (isDirty || isTouched) && error != null

    OutlinedTextField(
        value = value,
        onValueChange = { field.onChange(it) },
        modifier = modifier.onFocusChanged { if (!it.isFocused) field.onBlur() },
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder) }
        } else null,
        isError = showError,
        supportingText = when {
            isValidating -> {
                {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                        )
                        Text("Checking…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            showError -> {
                { Text(error!!, color = MaterialTheme.colorScheme.error) }
            }

            else -> null
        },
        visualTransformation = if (field.type.isSecret) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = field.type.keyboardHint.toComposeKeyboardType(),
            imeAction = imeAction,
        ),
        singleLine = true,
        enabled = enabled,
        trailingIcon = trailingIcon,
        colors = colors,
    )
}

/** Maps the library's platform-agnostic [KeyboardHint] to Compose's [KeyboardType]. */
private fun KeyboardHint.toComposeKeyboardType(): KeyboardType = when (this) {
    KeyboardHint.Text -> KeyboardType.Text
    KeyboardHint.Email -> KeyboardType.Email
    KeyboardHint.Password -> KeyboardType.Password
    KeyboardHint.Phone -> KeyboardType.Phone
    KeyboardHint.Number -> KeyboardType.Number
}
