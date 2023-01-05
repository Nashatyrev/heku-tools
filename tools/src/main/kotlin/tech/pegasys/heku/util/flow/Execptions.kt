package tech.pegasys.heku.util.flow

/**
 * Generic Flow exception
 */
open class HekuFlowException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Thrown downstream when flow buffer reaches it maximum capacity
 */
class BufferOverflowHekuFlowException(message: String) : HekuFlowException(message)

/**
 * When upstream Flow reports an exception it could be rethrown with [UpstreamHekuFlowException]
 */
class UpstreamHekuFlowException(message: String, cause: Throwable) : HekuFlowException(message, cause)

