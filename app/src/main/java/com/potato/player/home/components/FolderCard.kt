package com.potato.player.home.components

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.home.FolderGroup

@Composable
fun FolderCard(
    folderGroup: FolderGroup,
    onToggleExpand: () -> Unit,
    onFilePicked: (Uri) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .animateContentSize()
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161616), RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderGroup.folderName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${folderGroup.files.size} files",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                }
                
                val rotation by animateFloatAsState(targetValue = if (folderGroup.isExpanded) 180f else 0f)
                
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Expand/Collapse",
                    tint = Color(0xFF555555),
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }
        }
        
        if (folderGroup.isExpanded) {
            HorizontalDivider(color = Color(0xFF222222))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212), RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
            ) {
                folderGroup.files.forEachIndexed { index, file ->
                    MediaFileRow(
                        file = file,
                        onClick = { onFilePicked(file.uri) }
                    )
                    if (index < folderGroup.files.size - 1) {
                        HorizontalDivider(
                            color = Color(0xFF1E1E1E),
                            modifier = Modifier.padding(start = 94.dp)
                        )
                    }
                }
            }
        }
    }
}
