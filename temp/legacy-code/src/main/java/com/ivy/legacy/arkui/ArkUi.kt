package com.ivy.legacy.arkui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style

/**
 * 方舟档案风双语标题（PRTS Design A11）：
 * 左侧 4px 主色色条 + 中文大字 + 英文大写小字（0.08em 字距、muted 灰）。
 * "中文说事，英文做纹理"。
 */
@Composable
fun ArkBilingualTitle(
    cn: String,
    en: String,
    modifier: Modifier = Modifier,
    withBar: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (withBar) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .background(UI.colors.primary)
            )
            Spacer(Modifier.width(10.dp))
        }

        Text(
            text = cn,
            style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
        )

        Spacer(Modifier.width(8.dp))

        Text(
            text = en,
            style = UI.typo.c.style(
                fontWeight = FontWeight.Bold,
                color = UI.colors.gray
            ).copy(
                fontSize = 11.sp,
                letterSpacing = 0.08.em
            )
        )
    }
}
