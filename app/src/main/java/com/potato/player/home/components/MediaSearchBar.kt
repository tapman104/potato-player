package com.potato.player.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MediaSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
            .background(Color(0xFF1E1E24), RoundedCornerShape(14.dp)),
        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        cursorBrush = SolidColor(Color(0xFF6C63FF)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = Color(0xFF555555), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search media...", color = Color(0xFF444444), fontSize = 14.sp)
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear",
                        tint = Color(0xFF666666),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        }
    )
}
