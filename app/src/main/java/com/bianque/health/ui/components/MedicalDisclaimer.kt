package com.bianque.health.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 医疗健康合规免责声明组件
 *
 * 在 AI 诊断/内容生成页面底部显示，符合《互联网诊疗管理办法》要求
 */
@Composable
fun MedicalDisclaimer(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF8D6E3F) // Warm40
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp).padding(top = 1.dp)
            )
            Text(
                text = "本内容仅供健康参考，不作为医疗诊断依据。如有身体不适，请及时就医，切勿自行用药。",
                color = color,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Start
            )
        }
    }
}

/**
 * LLM 内容标签 — 标注内容由 AI 生成
 */
@Composable
fun AiGeneratedLabel(
    source: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFFE8F5E9), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "AI辅助生成 · 来源：$source",
            fontSize = 10.sp,
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 人工核对标签 — 标注内容已人工核对
 */
@Composable
fun HumanVerifiedLabel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color(0xFFFFF3E0), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "✓ 人工核对",
            fontSize = 10.sp,
            color = Color(0xFFE65100),
            fontWeight = FontWeight.Medium
        )
    }
}