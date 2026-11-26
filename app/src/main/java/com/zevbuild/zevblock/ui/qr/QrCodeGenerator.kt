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
 * Pure-Kotlin ISO/IEC 18004 QR Code encoder & Compose Canvas renderer.
 * Supports Versions 1-9 (up to 230 UTF-8 bytes) with Error Correction Level L,
 * Reed-Solomon GF(256) error correction codewords, alignment patterns,
 * BCH format & version metadata, and optimal mask selection.
 */
object QrCodeGenerator {

    private data class VersionSpec(
        val version: Int,
        val size: Int,
        val totalCodewords: Int,
        val dataCodewords: Int,
        val blockCount: Int,
        val alignmentCoords: IntArray,
        val remainderBits: Int
    )

    // ISO/IEC 18004 Table 9 specs for Error Correction Level L (Versions 1..9)
    private val VERSION_SPECS_L = listOf(
        VersionSpec(1, 21, 26, 19, 1, intArrayOf(), 0),
        VersionSpec(2, 25, 44, 34, 1, intArrayOf(6, 18), 7),
        VersionSpec(3, 29, 70, 55, 1, intArrayOf(6, 22), 7),
        VersionSpec(4, 33, 100, 80, 1, intArrayOf(6, 26), 7),
        VersionSpec(5, 37, 134, 108, 1, intArrayOf(6, 30), 7),
        VersionSpec(6, 41, 172, 136, 2, intArrayOf(6, 34), 7),
        VersionSpec(7, 45, 196, 156, 2, intArrayOf(6, 22, 38), 0),
        VersionSpec(8, 49, 242, 194, 2, intArrayOf(6, 24, 42), 0),
        VersionSpec(9, 53, 292, 232, 2, intArrayOf(6, 26, 46), 0)
    )

    // GF(256) with primitive polynomial x^8 + x^4 + x^3 + x^2 + 1 (0x11D)
    private val GF_EXP = IntArray(512)
    private val GF_LOG = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            GF_EXP[i] = x
            GF_LOG[x] = i
            x = x shl 1
            if ((x and 0x100) != 0) {
                x = x xor 0x11D
            }
        }
        for (i in 255 until 512) {
            GF_EXP[i] = GF_EXP[i - 255]
        }
    }

    /**
     * Encodes [text] into a scannable ISO/IEC 18004 QR boolean matrix.
     */
    fun encodeToMatrix(text: String, preferredMinVersion: Int = 1): Array<BooleanArray> {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        // Byte mode header uses 4 bits mode + 8 bits char count + 4 bits terminator overhead (2 bytes)
        val neededDataBytes = rawBytes.size + 2
        val spec = VERSION_SPECS_L.firstOrNull {
            it.version >= preferredMinVersion && it.dataCodewords >= neededDataBytes
        } ?: VERSION_SPECS_L.last()

        val payloadBytes = if (rawBytes.size > spec.dataCodewords - 2) {
            rawBytes.copyOf(spec.dataCodewords - 2)
        } else {
            rawBytes
        }

        val dataCodewords = encodeDataCodewords(payloadBytes, spec.dataCodewords)
        val finalBitStream = buildFinalBitStream(dataCodewords, spec)

        val size = spec.size
        val isFunction = Array(size) { BooleanArray(size) }
        val baseMatrix = Array(size) { BooleanArray(size) }

        placeFunctionPatterns(baseMatrix, isFunction, spec)
        placeDataBits(baseMatrix, isFunction, finalBitStream)

        // Evaluate masks 0..7 and select lowest penalty score per ISO 18004
        var bestMatrix: Array<BooleanArray>? = null
        var bestScore = Int.MAX_VALUE

        for (mask in 0..7) {
            val candidate = Array(size) { r -> baseMatrix[r].clone() }
            applyMask(candidate, isFunction, mask)
            applyFormatAndVersionBits(candidate, spec.version, mask)
            val score = evaluatePenalty(candidate)
            if (score < bestScore) {
                bestScore = score
                bestMatrix = candidate
            }
        }

        return bestMatrix!!
    }

    private fun encodeDataCodewords(payload: ByteArray, capacity: Int): IntArray {
        val bits = ArrayList<Int>(capacity * 8)

        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }

        // Byte mode indicator (0100) + 8-bit length (valid for Versions 1..9)
        appendBits(0b0100, 4)
        appendBits(payload.size, 8)
        for (b in payload) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val totalBits = capacity * 8
        val terminatorLen = minOf(4, totalBits - bits.size)
        repeat(terminatorLen) { bits.add(0) }

        while (bits.size % 8 != 0) {
            bits.add(0)
        }

        val codewords = IntArray(capacity)
        var byteIdx = 0
        var i = 0
        while (i < bits.size && byteIdx < capacity) {
            var value = 0
            for (b in 0 until 8) {
                value = (value shl 1) or bits[i + b]
            }
            codewords[byteIdx++] = value
            i += 8
        }

        var padIndex = 0
        while (byteIdx < capacity) {
            codewords[byteIdx++] = if (padIndex % 2 == 0) 0xEC else 0x11
            padIndex++
        }

        return codewords
    }

    private fun buildFinalBitStream(dataCodewords: IntArray, spec: VersionSpec): BooleanArray {
        val blockCount = spec.blockCount
        val eccPerBlock = (spec.totalCodewords - spec.dataCodewords) / blockCount
        val dataPerBlock = spec.dataCodewords / blockCount

        val dataBlocks = Array(blockCount) { blockIdx ->
            val start = blockIdx * dataPerBlock
            dataCodewords.copyOfRange(start, start + dataPerBlock)
        }

        val generator = computeGeneratorPoly(eccPerBlock)
        val eccBlocks = Array(blockCount) { blockIdx ->
            computeReedSolomonEcc(dataBlocks[blockIdx], generator)
        }

        val interleaved = ArrayList<Int>(spec.totalCodewords)
        for (i in 0 until dataPerBlock) {
            for (b in 0 until blockCount) {
                interleaved.add(dataBlocks[b][i])
            }
        }
        for (i in 0 until eccPerBlock) {
            for (b in 0 until blockCount) {
                interleaved.add(eccBlocks[b][i])
            }
        }

        val totalBits = interleaved.size * 8 + spec.remainderBits
        val bits = BooleanArray(totalBits)
        var pos = 0
        for (cw in interleaved) {
            for (bit in 7 downTo 0) {
                bits[pos++] = ((cw ushr bit) and 1) == 1
            }
        }
        return bits
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return GF_EXP[GF_LOG[a] + GF_LOG[b]]
    }

    private fun computeGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = GF_EXP[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun computeReedSolomonEcc(data: IntArray, generator: IntArray): IntArray {
        val eccLen = generator.size - 1
        val work = IntArray(data.size + eccLen)
        System.arraycopy(data, 0, work, 0, data.size)

        for (i in data.indices) {
            val factor = work[i]
            if (factor != 0) {
                for (j in generator.indices) {
                    work[i + j] = work[i + j] xor gfMul(generator[j], factor)
                }
            }
        }
        return work.copyOfRange(data.size, work.size)
    }

    private fun placeFunctionPatterns(
        matrix: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        spec: VersionSpec
    ) {
        val size = spec.size

        fun setFn(r: Int, c: Int, value: Boolean) {
            matrix[r][c] = value
            isFunction[r][c] = true
        }

        // Finder patterns + 1px white separator
        fun drawFinder(startR: Int, startC: Int) {
            for (dr in -1..7) {
                for (dc in -1..7) {
                    val r = startR + dr
                    val c = startC + dc
                    if (r !in 0 until size || c !in 0 until size) continue
                    val dark = dr in 0..6 && dc in 0..6 && (
                        dr == 0 || dr == 6 || dc == 0 || dc == 6 || (dr in 2..4 && dc in 2..4)
                    )
                    setFn(r, c, dark)
                }
            }
        }

        drawFinder(0, 0)
        drawFinder(0, size - 7)
        drawFinder(size - 7, 0)

        // Timing patterns
        for (i in 8 until size - 8) {
            val dark = (i % 2 == 0)
            setFn(6, i, dark)
            setFn(i, 6, dark)
        }

        // Alignment patterns
        val coords = spec.alignmentCoords
        for (rCenter in coords) {
            for (cCenter in coords) {
                if (isFunction[rCenter][cCenter]) continue
                for (dr in -2..2) {
                    for (dc in -2..2) {
                        val dark = dr == -2 || dr == 2 || dc == -2 || dc == 2 || (dr == 0 && dc == 0)
                        setFn(rCenter + dr, cCenter + dc, dark)
                    }
                }
            }
        }

        // Dark module
        setFn(size - 8, 8, true)

        // Reserve format info strips
        for (i in 0..8) {
            if (i != 6) {
                isFunction[8][i] = true
                isFunction[i][8] = true
            }
        }
        for (i in 0..7) {
            isFunction[8][size - 1 - i] = true
            if (i < 7) isFunction[size - 1 - i][8] = true
        }

        // Reserve version info blocks for Version 7+
        if (spec.version >= 7) {
            for (i in 0 until 6) {
                for (j in 0 until 3) {
                    isFunction[i][size - 11 + j] = true
                    isFunction[size - 11 + j][i] = true
                }
            }
        }
    }

    private fun placeDataBits(
        matrix: Array<BooleanArray>,
        isFunction: Array<BooleanArray>,
        bits: BooleanArray
    ) {
        val size = matrix.size
        var bitIndex = 0
        var upward = true
        var right = size - 1

        while (right >= 1) {
            if (right == 6) right--
            for (step in 0 until size) {
                val r = if (upward) size - 1 - step else step
                for (offset in 0..1) {
                    val c = right - offset
                    if (!isFunction[r][c]) {
                        val bit = if (bitIndex < bits.size) bits[bitIndex++] else false
                        matrix[r][c] = bit
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(matrix: Array<BooleanArray>, isFunction: Array<BooleanArray>, mask: Int) {
        val size = matrix.size
        for (r in 0 until size) {
            for (c in 0 until size) {
                if (isFunction[r][c]) continue
                val invert = when (mask) {
                    0 -> (r + c) % 2 == 0
                    1 -> r % 2 == 0
                    2 -> c % 3 == 0
                    3 -> (r + c) % 3 == 0
                    4 -> ((r / 2) + (c / 3)) % 2 == 0
                    5 -> (r * c) % 2 + (r * c) % 3 == 0
                    6 -> (((r * c) % 2) + ((r * c) % 3)) % 2 == 0
                    7 -> (((r + c) % 2) + ((r * c) % 3)) % 2 == 0
                    else -> false
                }
                if (invert) matrix[r][c] = !matrix[r][c]
            }
        }
    }

    private fun applyFormatAndVersionBits(matrix: Array<BooleanArray>, version: Int, mask: Int) {
        val size = matrix.size
        // ECC Level L (01) + 3-bit mask -> BCH(15,5)
        val data = (0b01 shl 3) or mask
        var rem = data shl 10
        val poly = 0x537
        for (bit in 14 downTo 10) {
            if (((rem ushr bit) and 1) != 0) {
                rem = rem xor (poly shl (bit - 10))
            }
        }
        val formatBits = ((data shl 10) or rem) xor 0x5412

        // Bit 0 (LSB)..14 (MSB) placement per ISO/IEC 18004 Fig 19
        for (i in 0..14) {
            val bit = ((formatBits ushr i) and 1) == 1
            // Primary copy around top-left finder
            when (i) {
                in 0..5 -> matrix[i][8] = bit
                6 -> matrix[7][8] = bit
                7 -> matrix[8][8] = bit
                8 -> matrix[8][7] = bit
                else -> matrix[8][14 - i] = bit // i=9..14 -> col=5..0
            }
            // Secondary copies at top-right horizontal (bits 0..7) & bottom-left vertical (bits 8..14)
            if (i < 8) {
                matrix[8][size - 1 - i] = bit
            } else {
                matrix[size - 15 + i][8] = bit
            }
        }

        if (version >= 7) {
            var vRem = version shl 12
            val vPoly = 0x1F25
            for (bit in 17 downTo 12) {
                if (((vRem ushr bit) and 1) != 0) {
                    vRem = vRem xor (vPoly shl (bit - 12))
                }
            }
            val versionBits = (version shl 12) or vRem
            for (i in 0 until 18) {
                val bit = ((versionBits ushr i) and 1) == 1
                val a = i / 3
                val b = i % 3
                matrix[a][size - 11 + b] = bit
                matrix[size - 11 + b][a] = bit
            }
        }
    }

    private fun evaluatePenalty(matrix: Array<BooleanArray>): Int {
        val size = matrix.size
        var penalty = 0

        // Rule 1: 5+ consecutive same-color modules in row or column
        for (r in 0 until size) {
            var runLen = 1
            for (c in 1 until size) {
                if (matrix[r][c] == matrix[r][c - 1]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }
        for (c in 0 until size) {
            var runLen = 1
            for (r in 1 until size) {
                if (matrix[r][c] == matrix[r - 1][c]) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runLen = 1
                }
            }
        }

        // Rule 2: 2x2 blocks
        for (r in 0 until size - 1) {
            for (c in 0 until size - 1) {
                val v = matrix[r][c]
                if (matrix[r][c + 1] == v && matrix[r + 1][c] == v && matrix[r + 1][c + 1] == v) {
                    penalty += 3
                }
            }
        }

        return penalty
    }
}

/**
 * Compose visual component for rendering the generated QR Code with a crisp quiet zone.
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
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor)
            .border(2.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp))
            .padding(16.dp)
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
