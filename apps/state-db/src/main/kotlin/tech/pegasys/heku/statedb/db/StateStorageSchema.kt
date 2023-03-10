package tech.pegasys.heku.statedb.db

import tech.pegasys.heku.statedb.diff.*
import tech.pegasys.heku.statedb.schema.SchemasBuilder
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.statedb.schema.StateIdCalculator
import tech.pegasys.heku.statedb.ssz.GIndexSelector
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
import tech.pegasys.heku.statedb.ssz.invert
import tech.pegasys.heku.util.type.ETime
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.heku.util.type.epochs
import tech.pegasys.teku.spec.SpecMilestone

class StateStorageSchema(
    val diffStoreFact: DiffStoreFactory,
    val indexedSszSrc: IndexedSszSource,
    val minSlot: Slot
) {

    val schema = SchemasBuilder.build {
        indexedSszSource = indexedSszSrc
        diffStoreFactory = diffStoreFact
        minimalSlot = minSlot

        val eearSchema = newSingleParentSchema {
            asRootSchema()
            diffSchema = SnapshotDiffSchema()
                .snappied()
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EEAR.epochs)
            name = "eearSchema"
        }

        val eonthSchema = newSingleParentSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eearSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EONTH.epochs)
            name = "eonthSchema"
        }

        val eekSchema = newSingleParentSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eonthSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EEK.epochs)
            name = "eekSchema"
        }

        val eaySchema = newSingleParentSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eekSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EAY.epochs)
            name = "eaySchema"
        }

        val balancesFieldSelector = GIndexSelector.beaconStateFieldSelector(SpecMilestone.ALTAIR, "balances")
        val nonBalancesFieldSelector = balancesFieldSelector.invert()

        val balancesCompositeSchema =
            CompositeDiffSchema(
                SimpleSszDiffSchema()
                    .toSparse(nonBalancesFieldSelector)
                    .gzipped(),
                UInt64DiffSchema()
                    .toSparse(balancesFieldSelector)
                    .gzipped()
            )

        val epochX64Schema = newSingleParentSchema {
            diffSchema = balancesCompositeSchema
            parentSchema = eaySchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(64.epochs)
            name = "epochX64Schema"
        }

        val epochX16Schema = newSingleParentSchema {
            diffSchema = balancesCompositeSchema
            parentSchema = epochX64Schema
            stateIdCalculator = StateIdCalculator.everyNEpochs(16.epochs)

            // workaround to avoid double calculation of epochX16Schema bytes
            // TODO: we need a stateless mechanism to reuse result required by several distinct children schemas
            //       this is to avoid cached value to be retained in memory after load() call
            resultCacheSize = 1

            name = "epochX16Schema"
        }


        newMergeSchema {
            parentDelegate = epochX16Schema

            addSingleParentSchema {
                diffSchema = SimpleSszDiffSchema()
                    .toSparse(nonBalancesFieldSelector)
                    .gzipped()
                parentSchema = epochX16Schema
                stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
                name = "epochX1RestSchema"
            }

            addSingleParentSchema {
                diffSchema = UInt64DiffSchema()
                    .toSparse(balancesFieldSelector)
                    .gzipped()
                parentSchema = epochX16Schema
                sameSchemaUntilParent { StateId(it.slot - 1.epochs) }
                stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
                name = "epochX1BalancesSchema"
            }
        }
    }

}