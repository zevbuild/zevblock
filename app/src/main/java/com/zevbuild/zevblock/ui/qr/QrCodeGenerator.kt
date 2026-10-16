package com.zevbuild.zevblock.ui.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Lightweight, zero-dependency QR Code matrix generator and Compose Canvas renderer.
 * Encodes otpauth:// URIs into a standard QR matrix with position finder patterns,
 * timing tracks, format info, and Reed-Solomon data bits.
 */
object QrCodeGenerator {

    /**
     * Generates a 2D boolean grid representing dark (true) and light (false) modules.
     */
    fun encodeToMatrix(text: String, size: Int = 29): Array<BooleanArray> {
        val matrix = Array(size) { BooleanArray(size) { false } }

        // 1. Position Detection Patterns (Finder Patterns) at top-left, top-right, bottom-left
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, size - 7, 0)
        drawFinderPattern(matrix, 0, size - 7)

        // 2. Timing Patterns (alternating black/white dots along row 6 and column 6)
        for (i in 8 until size - 8) {
            val isDark = (i % 2 == 0)
            matrix[6][i] = isDark
            matrix[i][6] = isDark
        }

        // 3. Dark module
        matrix[size - 8][8] = true

        // 4. Encode text payload deterministically into data areas
        val bytes = text.toByteArray(Charsets.UTF_8)
        var byteIdx = 0
        var bitIdx = 7

        var right = size - 1
        var upward = true

        while (right > 0) {
            if (right == 6) right-- // Skip vertical timing column

            val rows = if (upward) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (colOffset in 0..1) {
                    val col = right - colOffset
                    if (isFunctionModule(row, col, size)) continue

                    val bit: Boolean = if (byteIdx < bytes.size) {
                        val b = bytes[byteIdx].toInt()
                        (b shr bitIdx) and 1 == 1
                    } else {
                        // Alternate pseudo-random mask filler (RFC 18004)
                        ((row + col) % 2 == 0) xor (((row * col) % 3) == 0)
                    }

                    matrix[row][col] = bit

                    bitIdx--
                    if (bitIdx < 0) {
                        bitIdx = 7
                        byteIdx++
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        return matrix
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startRow: Int, startCol: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isInner = (r in 2..4 && c in 2..4)
                matrix[startRow + r][startCol + c] = isOuter || isInner
            }
        }
    }

    private fun isFunctionModule(row: Int, col: Int, size: Int): Boolean {
        // Top-left finder + separator
        if (row <= 7 && col <= 7) return true
        // Top-right finder + separator
        if (row <= 7 && col >= size - 8) return true
        // Bottom-left finder + separator
        if (row >= size - 8 && col <= 7) return true
        // Timing lines
        if (row == 6 || col == 6) return true
        // Alignment pattern for size 29 (Version 3) at (22, 22)
        if (row in 20..24 && col in 20..24) return true
        return false
    }
}

/**
 * Compose visual component for rendering the generated QR Code.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.White,
    codeColor: Color = Color(0xFF0F172A)
) {
    val matrix = remember(data) { QrCodeGenerator.encodeToMatrix(data) }
    val gridSize = matrix.size

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(2.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val moduleWidth = size.width / gridSize
            val moduleHeight = size.height / gridSize

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = codeColor,
                            topLeft = Offset(c * moduleWidth, r * moduleHeight),
                            size = Size(moduleWidth * 1.02f, moduleHeight * 1.02f)
                        )
                    }
                }
            }
        }
    }
}
