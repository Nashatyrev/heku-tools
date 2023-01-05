package tech.pegasys.heku.util.stat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TableTest {

    @Test
    fun sanity() {

        println(0..3)

        val table = mapOf(
            "A" to listOf(11, 12, 13, 14),
            "B" to listOf(21, 22),
            "C" to listOf(31, 32, 33, 34, 35)
        )

        println(table.toStringTransposed(","))

        val list = listOf(
            5 to 10,
            1 to 11,
            7 to 12,
            2 to 13,
            10 to 14,
            0 to 666
        )

        println(list.groupByRanges(1..4, 5..10))

        val map = mapOf(
            "B" to mapOf(
                "2" to "b2",
                "1" to "b1",
            ),
            "A" to mapOf(
                "2" to "a2",
                "1" to "a1",
            )
        )

        val table1 = map
            .toTable(tableFillDefault(""), false);

        table1
            .transposed()
            .print()
            .also { println(it) }

        val map1 = mapOf(
            "Booo" to mapOf(
                "222222" to "b2",
                "1" to "b111",
            ),
            "At" to mapOf(
                "222222" to "a2222222",
                "1" to "a1",
            )
        )

        val table2 = map1
            .toTable(tableFillDefault(""), false);
        table2.printPretty().also { println(it) }

        val table3 = table2.insertColumn(1, "333", listOf("33", "3333"))
        table3.printPretty().also { println(it) }
    }

    @Test
    fun `test multiple column sort`() {
        val data = mapOf(
            "A" to listOf("a", "b", "a"),
            "B" to listOf(1, 2, 3)
        )
        val table = Table.fromColumns(data)

        run {
            val sortedTable = table.sorted {
                sortAscending("A")
                sortDescending("B")
            }

            println(sortedTable.printPretty())
            assertThat(sortedTable.rowNames).isEqualTo(listOf(2, 0, 1))
        }

        run {
            val sortedTable = table.sorted {
                sortDescending("A")
                sortAscending("B")
            }

            println(sortedTable.printPretty())
            assertThat(sortedTable.rowNames).isEqualTo(listOf(1, 0, 2))
        }

        run {
            val sortedTable = table.sorted {
                sortDescending("B")
            }

            println(sortedTable.printPretty())
            assertThat(sortedTable.rowNames).isEqualTo(listOf(2, 1, 0))
        }
    }
}
