package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.db.DiffStoreFactory
import tech.pegasys.heku.statedb.diff.DiffSchema
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
import tech.pegasys.heku.util.type.Slot

typealias ParentSelector = (thisSchema: DagSchema, stateId: StateId) -> DagSchemaVertex?

class SchemasBuilder {

    var diffStoreFactory: DiffStoreFactory? = null
    var indexedSszSource: IndexedSszSource? = null
    var minimalSlot: Slot = Slot(0)

    val schemaToBuilder = mutableMapOf<DagSchema, HierarchicalSchemaBuilder>()

    fun newHierarchicalSchema(block: HierarchicalSchemaBuilder.() -> Unit): DagSchema {
        val builder = HierarchicalSchemaBuilder()
        block(builder)
        val schema = builder.build()
        schemaToBuilder[schema] = builder
        return schema
    }

    val DagSchema.stateIdCalculator get() = schemaToBuilder[this]!!.stateIdCalculator!!

    fun newMergeSchema(block: MergeSchemaBuilder.() -> Unit): MergeSchema {
        val builder = MergeSchemaBuilder()
        block(builder)
        val schema = builder.build()
        return schema
    }

    interface AbstractSchemaBuilder {
        var parentSchema: DagSchema?

    }

    inner class MergeSchemaBuilder {
        var parentDelegate: DagSchema? = null

        private val parentSchemas = mutableListOf<DagSchema>()

        fun addHierarchicalSchema(block: HierarchicalSchemaBuilder.() -> Unit): DagSchema {
            val builder = HierarchicalSchemaBuilder()
            block(builder)
            val schema = builder.build()
            parentSchemas += schema
            return schema
        }

        fun build(): MergeSchema {
            if (parentDelegate == null) {
                return MergeSchema(parentSchemas)
            } else {
                return object : MergeSchema(parentSchemas) {
                    override fun getParents(stateId: StateId): List<DagSchemaVertex> {
                        return if (parentDelegate!!.stateIdCalculator.isSame(stateId)) {
                            listOf(DagSchemaVertex(stateId, parentDelegate!!))
                        } else {
                            super.getParents(stateId)
                        }
                    }
                }
            }
        }
    }

    inner class HierarchicalSchemaBuilder : AbstractSchemaBuilder {
        var diffSchema: DiffSchema? = null
        var rootSchema = false
        override var parentSchema: DagSchema? = null
        var stateIdCalculator: StateIdCalculator? = null
        var parentSelector: ParentSelector? = null
        var name = "<noname>"
        var cacheSize = 0

        fun asRootSchema() { rootSchema = true }

        fun sameSchemaUntilParent(parentStateIdCalculator: StateIdCalculator) {
            requireNotNull(parentSchema) { "parentSchema should be set before"}
            val boundedParentStateIdCalculator = parentStateIdCalculator.withMinimalSlot(minimalSlot)
            val parentSchemaStateIdCalculator = parentSchema!!.stateIdCalculator
            parentSelector = { thisSchema, stateId ->
                val parentStateId = boundedParentStateIdCalculator.calculateStateId(stateId)
                DagSchemaVertex(
                    parentStateId,
                    if (parentSchemaStateIdCalculator.isSame(parentStateId)) {
                        parentSchema!!
                    } else {
                        thisSchema
                    }
                )
            }
        }

        fun build(): HierarchicalSchema {
            require(parentSchema != null || rootSchema) { "Should be declared either as root schema or parent schema should be set" }
            requireNotNull(stateIdCalculator)
            stateIdCalculator = stateIdCalculator!!.withMinimalSlot(minimalSlot)

            val pSelector = parentSelector ?: { thisSchema, stateId ->
                if (rootSchema) {
                    null
                } else {
                    val parentSchemaBuilder = schemaToBuilder[parentSchema]
                    val parentStateIdCalculator = parentSchemaBuilder!!.stateIdCalculator!!
                    DagSchemaVertex(parentStateIdCalculator.calculateStateId(stateId), parentSchema!!)
                }
            }

            return object : HierarchicalSchema(
                diffSchema ?: throw IllegalStateException("diffSchema should be set"),
                diffStoreFactory?.createStore() ?: throw IllegalStateException("diffStoreFactory should be set"),
                indexedSszSource ?: throw IllegalStateException("indexedSszSource should be set"),
                true,
                name,
                cacheSize
            ) {
                override fun getParent(stateId: StateId): DagSchemaVertex? = pSelector(this, stateId)
            }
        }
    }

    companion object {
        fun build(block: SchemasBuilder.() -> AbstractSchema): AbstractSchema {
            val schemasBuilder = SchemasBuilder()
            val finalSchema = block(schemasBuilder)
            return finalSchema
        }
    }
}
