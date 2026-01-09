package com.bylins.client.network

class MsdpParser {
    companion object {
        const val MSDP_VAR: Byte = 1
        const val MSDP_VAL: Byte = 2
        const val MSDP_TABLE_OPEN: Byte = 3
        const val MSDP_TABLE_CLOSE: Byte = 4
        const val MSDP_ARRAY_OPEN: Byte = 5
        const val MSDP_ARRAY_CLOSE: Byte = 6
    }

    fun parse(data: ByteArray): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        var pos = 0

        while (pos < data.size) {
            if (data[pos] == MSDP_VAR) {
                pos++
                val varName = readString(data, pos)
                pos += varName.length

                if (pos < data.size && data[pos] == MSDP_VAL) {
                    pos++
                    val (value, newPos) = readValue(data, pos)
                    result[varName] = value
                    pos = newPos
                }
            } else {
                pos++
            }
        }

        return result
    }

    private fun readString(data: ByteArray, start: Int): String {
        val end = data.indexOfFirst(start) { byte ->
            byte == MSDP_VAR || byte == MSDP_VAL ||
            byte == MSDP_TABLE_OPEN || byte == MSDP_TABLE_CLOSE ||
            byte == MSDP_ARRAY_OPEN || byte == MSDP_ARRAY_CLOSE
        }

        val endPos = if (end == -1) data.size else end
        return String(data.sliceArray(start until endPos), Charsets.UTF_8)
    }

    private fun readValue(data: ByteArray, start: Int): Pair<Any, Int> {
        var pos = start

        return when {
            pos < data.size && data[pos] == MSDP_ARRAY_OPEN -> {
                pos++
                val array = mutableListOf<String>()

                while (pos < data.size && data[pos] != MSDP_ARRAY_CLOSE) {
                    val str = readString(data, pos)
                    if (str.isNotEmpty()) {
                        array.add(str)
                    }
                    pos += str.length
                    if (pos < data.size && (data[pos] == MSDP_VAL || data[pos] == MSDP_ARRAY_CLOSE)) {
                        if (data[pos] == MSDP_VAL) pos++
                        if (data[pos] == MSDP_ARRAY_CLOSE) break
                    }
                }

                if (pos < data.size && data[pos] == MSDP_ARRAY_CLOSE) {
                    pos++
                }

                Pair(array, pos)
            }

            pos < data.size && data[pos] == MSDP_TABLE_OPEN -> {
                pos++
                val table = mutableMapOf<String, Any>()

                while (pos < data.size && data[pos] != MSDP_TABLE_CLOSE) {
                    if (data[pos] == MSDP_VAR) {
                        pos++
                        val key = readString(data, pos)
                        pos += key.length

                        if (pos < data.size && data[pos] == MSDP_VAL) {
                            pos++
                            val (value, newPos) = readValue(data, pos)
                            table[key] = value
                            pos = newPos
                        }
                    } else {
                        pos++
                    }
                }

                if (pos < data.size && data[pos] == MSDP_TABLE_CLOSE) {
                    pos++
                }

                Pair(table, pos)
            }

            else -> {
                val str = readString(data, pos)
                Pair(str, pos + str.length)
            }
        }
    }

    private fun ByteArray.indexOfFirst(start: Int, predicate: (Byte) -> Boolean): Int {
        for (i in start until size) {
            if (predicate(this[i])) {
                return i
            }
        }
        return -1
    }
}
