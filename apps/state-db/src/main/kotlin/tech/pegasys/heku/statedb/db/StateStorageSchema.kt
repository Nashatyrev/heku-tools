package tech.pegasys.heku.statedb.db

import tech.pegasys.heku.statedb.diff.*
import tech.pegasys.heku.statedb.schema.*
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
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

        val eearSchema = newHierarchicalSchema {
            asRootSchema()
            diffSchema = SnapshotDiffSchema()
                .gzipped()
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EEAR.epochs)
//            cacheSize = 1
        }

        val eonthSchema = newHierarchicalSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eearSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EONTH.epochs)
//            cacheSize = 1
        }

        val eekSchema = newHierarchicalSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eonthSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EEK.epochs)
//            cacheSize = 1
        }

        val eaySchema = newHierarchicalSchema {
            diffSchema = SimpleSszDiffSchema()
            parentSchema = eekSchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(ETime.EAY.epochs)
//            cacheSize = 1
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

        val epochX64Schema = newHierarchicalSchema {
            diffSchema = balancesCompositeSchema
            parentSchema = eaySchema
            stateIdCalculator = StateIdCalculator.everyNEpochs(64.epochs)
//            cacheSize = 1
        }

        val epochX16Schema = newHierarchicalSchema {
            diffSchema = balancesCompositeSchema
            parentSchema = epochX64Schema
            stateIdCalculator = StateIdCalculator.everyNEpochs(16.epochs)
//            cacheSize = 1
        }


        newMergeSchema {
            parentDelegate = epochX16Schema

            addHierarchicalSchema {
                diffSchema = SimpleSszDiffSchema()
                    .toSparse(nonBalancesFieldSelector)
                    .gzipped()
                parentSchema = epochX16Schema
                stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
            }

            addHierarchicalSchema {
                diffSchema = UInt64DiffSchema()
                    .toSparse(balancesFieldSelector)
                    .gzipped()
                parentSchema = epochX16Schema
                sameSchemaUntilParent { StateId(it.slot - 1.epochs) }
                stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
//                cacheSize = 16
            }
        }
    }

}