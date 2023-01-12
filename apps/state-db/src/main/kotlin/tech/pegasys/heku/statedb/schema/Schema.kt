package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.diff.DiffResult

interface Schema {

    suspend fun load(stateId: StateId): DiffResult

    suspend fun save(stateId: StateId)
}

