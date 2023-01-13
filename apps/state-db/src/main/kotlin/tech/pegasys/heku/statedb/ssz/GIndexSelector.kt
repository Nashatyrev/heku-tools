package tech.pegasys.heku.statedb.ssz

import tech.pegasys.heku.util.beacon.spec.spec
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.networks.Eth2Network

infix fun GIndexSelector.union(other: GIndexSelector) =
    GIndexSelector { this.isSelected(it) || other.isSelected(it) }
infix fun GIndexSelector.subtract(other: GIndexSelector) =
    GIndexSelector { this.isSelected(it) && !other.isSelected(it) }
fun GIndexSelector.invert() = GIndexSelector.ALL subtract this

fun interface GIndexSelector {

    fun isSelected(gIndex: Long): Boolean

    companion object {

        val ALL: GIndexSelector = GIndexSelector { true }

        fun successorIndexSelector(parentGIndex: Long, excludeParentIndex: Boolean = true) =
            GIndexSelector {
                val relation = GIndexUtil.gIdxCompare(parentGIndex, it)
                relation == GIndexUtil.NodeRelation.PREDECESSOR
                        || (!excludeParentIndex && relation == GIndexUtil.NodeRelation.SAME)
            }

        fun sszContainerFieldSelector(
            sszContainerSchema: SszContainerSchema<*>,
            fieldName: String,
            excludeFieldRootIndex: Boolean = true
        ): GIndexSelector {
            val fieldIndex = sszContainerSchema.getFieldIndex(fieldName)
            if (fieldIndex < 0) throw IllegalArgumentException("No field '$fieldName' found in schema $sszContainerSchema")
            val fieldGIndex = sszContainerSchema.getChildGeneralizedIndex(fieldIndex.toLong())
            return successorIndexSelector(fieldGIndex, excludeFieldRootIndex)
        }

        fun beaconStateFieldSelector(milestone: SpecMilestone, fieldName: String, excludeRootFieldNode: Boolean = true) =
            sszContainerFieldSelector(
                Eth2Network.MAINNET.spec().getInstantSpecAtMilestone(milestone).specVersion.schemaDefinitions.beaconStateSchema,
                fieldName,
                excludeRootFieldNode
            )
    }
}