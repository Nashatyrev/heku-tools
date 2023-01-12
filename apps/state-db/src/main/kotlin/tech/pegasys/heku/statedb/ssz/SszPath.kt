package tech.pegasys.heku.statedb.ssz

import tech.pegasys.heku.statedb.ssz.PathElementType.*
import tech.pegasys.teku.infrastructure.ssz.schema.*
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.*
import java.lang.Integer.max

private val SELECTOR_G_INDEX = GIndexUtil.RIGHT_CHILD_G_INDEX

enum class PathElementType { SELF, SELECTOR, SINGLE, RANGE }
val PathElementType.isTerminal get() = this in setOf(SELF, SELECTOR, RANGE)

class SszPathElement(
    val schema: SszSchema<*>,
    val childGIndex: Long
) {
    val childGIndexDepth = gIdxGetDepth(childGIndex)
    val type = run {
        when {
            childGIndex == GIndexUtil.SELF_G_INDEX ->
                SELF
            childGIndex == SELECTOR_G_INDEX && schema.hasSelectorNode() ->
                SELECTOR
            schema is SszCompositeSchema<*> && childGIndexDepth == schema.treeDepth() ->
                SINGLE
            schema is SszCompositeSchema<*> && childGIndexDepth < schema.treeDepth() ->
                RANGE
            else -> throw IllegalArgumentException("Invalid GIndex ${childGIndex.toString(2)} for schema $schema")
        }
    }

    fun getSchemaTreeDepth(): Int {
        val compositeSchema = schema as? SszCompositeSchema<*> ?: throw IllegalStateException("No treeDepth for schema $schema")
        return compositeSchema.treeDepth()
    }

    fun getChildIndex(): Int {
        if (type != SINGLE) {
            throw IllegalStateException("No child index for type $type (schema: $schema)")
        }
        return gIdxGetChildIndex(childGIndex, getSchemaTreeDepth())
    }

    fun getChildIndexRange(): IntRange =
        when(type) {
            SINGLE -> getChildIndex()..getChildIndex()
            RANGE -> {
                schema as SszCompositeSchema<*>
                val rangeFirst = gIdxGetChildIndex(gIdxLeftmostFrom(childGIndex), getSchemaTreeDepth())
                val rangeLast = gIdxGetChildIndex(gIdxRightmostFrom(childGIndex), getSchemaTreeDepth())
                rangeFirst..rangeLast
            }
            else -> throw IllegalStateException("No child index for type $type (schema: $schema)")

        }

    fun getChildSchema(): SszSchema<*>? =
        when {
            type == SINGLE && schema is SszCompositeSchema<*> ->
                schema.getChildSchema(getChildIndex())
            type == RANGE && schema is SszCollectionSchema<*,*> ->
                schema.elementSchema
            else ->
                null
        }

    fun toStringShort() =
        when(type) {
            SELF -> ""
            SELECTOR ->
                when(schema) {
                    is SszUnionSchema -> "\$SEL"
                    is SszListSchema<*,*> -> "\$LEN"
                    else -> throw IllegalStateException("Invalid schema: $schema")
                }
            SINGLE ->
                when(schema) {
                    is SszContainerSchema -> "/" + schema.fieldNames[getChildIndex()]
                    else -> "[${getChildIndex()}]"
                }
            RANGE -> "[${getChildIndexRange()}]"
        }

    override fun toString() = "<$schema>" + toStringShort()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as SszPathElement
        if (schema != other.schema) return false
        if (childGIndex != other.childGIndex) return false
        return true
    }

    override fun hashCode(): Int {
        return childGIndex.hashCode()
    }


    companion object {

        private fun SszSchema<*>.hasSelectorNode() = this is SszListSchema<*, *> || this is SszUnionSchema<*>

        private fun gIdxGIndexAtDepth(gIdx: Long, depth: Int): Long {
            val tailDepth = gIdxGetDepth(gIdx) - depth
            return gIdx ushr max(0, tailDepth)
        }

        fun create(schema: SszSchema<*>, gIndex: Long): SszPathElement {
            return if (schema is SszCompositeSchema<*>) {
                SszPathElement(schema, gIdxGIndexAtDepth(gIndex, schema.treeDepth()))
            } else {
                SszPathElement(schema, gIndex)
            }
        }
    }


}

class SszPath(
    val elements: List<SszPathElement>
) {

    init {
        require(elements.dropLast(1).all { !it.type.isTerminal })
        require(elements.last().type.isTerminal)
    }

    override fun toString() = elements.joinToString("") {it.toStringShort()}.ifEmpty { "/" }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        other as SszPath
        return elements != other.elements
    }

    override fun hashCode(): Int {
        return elements.hashCode()
    }

    companion object {

        fun create(schema: SszSchema<*>, gIndex: Long): SszPath {
            val elements = mutableListOf<SszPathElement>()
            var curSchema = schema
            var curGIdx = gIndex
            while (true) {
                val element = SszPathElement.create(curSchema, curGIdx)
                elements += element
                if (element.type.isTerminal) break
                curSchema = element.getChildSchema()!!
                curGIdx = gIdxGetRelativeGIndex(curGIdx, element.childGIndexDepth)
            }
            return SszPath(elements)
        }
    }
}