package com.maunkavach.ui.util

object QrCodeEncoder {
    private const val VERSION = 40
    private const val SIZE = 177
    private const val DATA_CODEWORDS = 3200
    private const val ECC_CODEWORDS = 64

    fun encode(text: String): Array<BooleanArray> {
        val data = makeDataCodewords(text.toByteArray(Charsets.UTF_8))
        val ecc = reedSolomonRemainder(data, ECC_CODEWORDS)
        val codewords = data + ecc

        val modules = Array(SIZE) { BooleanArray(SIZE) }
        val reserved = Array(SIZE) { BooleanArray(SIZE) }
        drawFunctionPatterns(modules, reserved)
        drawCodewords(codewords, modules, reserved)
        applyMask(modules, reserved)
        drawFormatBits(modules, reserved)
        return modules
    }

    private fun makeDataCodewords(bytes: ByteArray): IntArray {
        require(bytes.size <= DATA_CODEWORDS - 4) { "Contact QR payload is too large." }
        val bits = mutableListOf<Int>()
        appendBits(bits, 0b0100, 4)
        appendBits(bits, bytes.size, 16)
        bytes.forEach { appendBits(bits, it.toInt() and 0xFF, 8) }
        repeat(minOf(4, DATA_CODEWORDS * 8 - bits.size)) { bits.add(0) }
        while (bits.size % 8 != 0) bits.add(0)

        val data = mutableListOf<Int>()
        for (i in bits.indices step 8) {
            var value = 0
            repeat(8) { value = (value shl 1) or bits[i + it] }
            data.add(value)
        }
        var pad = 0
        while (data.size < DATA_CODEWORDS) {
            data.add(if (pad++ % 2 == 0) 0xEC else 0x11)
        }
        return data.toIntArray()
    }

    private fun appendBits(bits: MutableList<Int>, value: Int, count: Int) {
        for (i in count - 1 downTo 0) bits.add((value ushr i) and 1)
    }

    private fun drawFunctionPatterns(modules: Array<BooleanArray>, reserved: Array<BooleanArray>) {
        drawFinder(modules, reserved, 0, 0)
        drawFinder(modules, reserved, SIZE - 7, 0)
        drawFinder(modules, reserved, 0, SIZE - 7)

        for (i in 8 until SIZE - 8) {
            setFunction(modules, reserved, 6, i, i % 2 == 0)
            setFunction(modules, reserved, i, 6, i % 2 == 0)
        }

        listOf(30, 58, 86, 114, 142).forEach { y ->
            listOf(30, 58, 86, 114, 142).forEach { x ->
                drawAlignment(modules, reserved, x, y)
            }
        }
        setFunction(modules, reserved, 8, 4 * VERSION + 9, true)

        for (i in 0..8) {
            if (i != 6) {
                reserve(reserved, 8, i)
                reserve(reserved, i, 8)
            }
        }
        for (i in 0..7) reserve(reserved, SIZE - 1 - i, 8)
        for (i in 0..6) reserve(reserved, 8, SIZE - 7 + i)
    }

    private fun drawFinder(modules: Array<BooleanArray>, reserved: Array<BooleanArray>, x: Int, y: Int) {
        for (dy in -1..7) {
            for (dx in -1..7) {
                val xx = x + dx
                val yy = y + dy
                if (xx !in 0 until SIZE || yy !in 0 until SIZE) continue
                val dark = dx in 0..6 && dy in 0..6 &&
                    (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx in 2..4 && dy in 2..4))
                setFunction(modules, reserved, xx, yy, dark)
            }
        }
    }

    private fun drawAlignment(modules: Array<BooleanArray>, reserved: Array<BooleanArray>, centerX: Int, centerY: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dark = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy)) != 1
                setFunction(modules, reserved, centerX + dx, centerY + dy, dark)
            }
        }
    }

    private fun drawCodewords(codewords: IntArray, modules: Array<BooleanArray>, reserved: Array<BooleanArray>) {
        var bitIndex = 0
        var upward = true
        var right = SIZE - 1
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until SIZE) {
                val y = if (upward) SIZE - 1 - vert else vert
                for (dx in 0..1) {
                    val x = right - dx
                    if (!reserved[y][x] && bitIndex < codewords.size * 8) {
                        modules[y][x] = ((codewords[bitIndex / 8] ushr (7 - bitIndex % 8)) and 1) != 0
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(modules: Array<BooleanArray>, reserved: Array<BooleanArray>) {
        for (y in 0 until SIZE) {
            for (x in 0 until SIZE) {
                if (!reserved[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, reserved: Array<BooleanArray>) {
        val bits = formatBits(ecl = 1, mask = 0)
        for (i in 0 until 15) {
            val dark = ((bits ushr i) and 1) != 0
            when {
                i < 6 -> setFunction(modules, reserved, 8, i, dark)
                i == 6 -> setFunction(modules, reserved, 8, 7, dark)
                i == 7 -> setFunction(modules, reserved, 8, 8, dark)
                i == 8 -> setFunction(modules, reserved, 7, 8, dark)
                else -> setFunction(modules, reserved, 14 - i, 8, dark)
            }
            if (i < 8) {
                setFunction(modules, reserved, SIZE - 1 - i, 8, dark)
            } else {
                setFunction(modules, reserved, 8, SIZE - 15 + i, dark)
            }
        }
    }

    private fun formatBits(ecl: Int, mask: Int): Int {
        var data = (ecl shl 3) or mask
        var rem = data
        repeat(10) { rem = rem shl 1 }
        val generator = 0x537
        for (i in 14 downTo 10) {
            if (((rem ushr i) and 1) != 0) rem = rem xor (generator shl (i - 10))
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun reedSolomonRemainder(data: IntArray, degree: Int): IntArray {
        val generator = reedSolomonGenerator(degree)
        val result = IntArray(degree)
        data.forEach { value ->
            val factor = value xor result[0]
            for (i in 0 until degree - 1) result[i] = result[i + 1]
            result[degree - 1] = 0
            for (i in 0 until degree) result[i] = result[i] xor gfMultiply(generator[i], factor)
        }
        return result
    }

    private fun reedSolomonGenerator(degree: Int): IntArray {
        var result = intArrayOf(1)
        var root = 1
        repeat(degree) {
            val next = IntArray(result.size + 1)
            for (i in result.indices) {
                next[i] = next[i] xor gfMultiply(result[i], root)
                next[i + 1] = next[i + 1] xor result[i]
            }
            result = next
            root = gfMultiply(root, 0x02)
        }
        return result
    }

    private fun gfMultiply(a: Int, b: Int): Int {
        var x = a
        var y = b
        var product = 0
        while (y != 0) {
            if ((y and 1) != 0) product = product xor x
            x = x shl 1
            if ((x and 0x100) != 0) x = x xor 0x11D
            y = y ushr 1
        }
        return product and 0xFF
    }

    private fun setFunction(modules: Array<BooleanArray>, reserved: Array<BooleanArray>, x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        reserved[y][x] = true
    }

    private fun reserve(reserved: Array<BooleanArray>, x: Int, y: Int) {
        reserved[y][x] = true
    }
}
