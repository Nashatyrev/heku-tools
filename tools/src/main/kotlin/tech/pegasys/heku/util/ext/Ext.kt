package tech.pegasys.heku.util.ext

import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

fun <T> Optional<T>.toNullable(): T? = orElse(null)

fun Any.callPrivateMethod(methodName: String, vararg args: Any): Any? {
    val method =
        this.javaClass.getDeclaredMethod(methodName, *args.map { it.javaClass }.toTypedArray())
    method.isAccessible = true
    return method.invoke(this, *args)
}

fun Any.getPrivateField(fieldName: String): Any? {
    val field = this.javaClass.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
}

fun <C> Deferred<C>.orTimeout(timeout: Duration, scope: CoroutineScope = GlobalScope): Deferred<C> {
    return scope.async {
        withTimeout(timeout) {
            this@orTimeout.await()
        }
    }
}

fun Int.increasingSequence() = generateSequence({ this }, { it + 1 })
fun Int.decreasingSequence() = generateSequence({ this }, { it - 1 })

fun <T : Comparable<T>> max(t1: T, t2: T) = if (t1 > t2) t1 else t2

fun Duration.round(unit: DurationUnit): Duration =
    if (this.isFinite()) this.toLong(unit).toDuration(unit)
    else this


@Retention(AnnotationRetention.RUNTIME)
annotation class Display(
    val name: String
)

fun <T : Any> T.primaryConstructorValuesMap(processDisplayAnnotations: Boolean = true): Map<String, Any?> {
    val kClass = this::class as KClass<T>
    val propertyNames = kClass.primaryConstructor!!.parameters.map { it.name }
    val properties = propertyNames.map { name ->
        kClass.memberProperties.find { it.name == name }!!
    }
    return properties.associate { prop ->
        val annotation =
            if (processDisplayAnnotations)
                prop.findAnnotation<Display>()
            else null
        val key = annotation?.name ?: prop.name
        key to prop.get(this)
    }
}

fun <T: Any> Collection<T>.majority(): T =
    this.majorityOrNull() ?: throw IllegalArgumentException("Empty")

fun <T: Any> Collection<T>.majorityOrNull(): T? =
    this.countMap().firstNotNullOfOrNull { it.value }

fun <T: Any> Collection<T>.countMap(): Map<Int, T> =
    this
        .groupingBy { it }
        .eachCount()
        .entries
        .map { it.value to it.key }
        .sortedBy { it.first }
        .toMap()

fun <T : Any> T.peekIf(condition: Boolean, block: T.() -> Unit): T {
    if (condition) {
        block(this)
    }
    return this
}

fun <T> CompletableFuture<T>.orTimeoutK(timeout: Duration) = this.orTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)

inline fun <K, V> MutableMap<K, V>.getOrCompute(key: K, func: (K) -> V): V =
    this[key] ?: run {
        val newValue = func(key)
        this[key] = newValue
        newValue
    }

fun <T> Iterator<T>.take(count: Int): List<T> {
    val ret = mutableListOf<T>()
    while (this.hasNext() && ret.size < count) {
        ret += this.next()
    }
    return ret
}

fun <T> MutableList<T>.removeFirst(count: Int) {
    for (i in 0 until count) {
        if (this.removeFirstOrNull() == null) break
    }
}

fun <T> MutableList<T>.removeFirstWhile(predicate: (T) -> Boolean) {
    while (this.isNotEmpty() && predicate(this[0])) {
        this.removeFirst()
    }
}

fun <T> Iterable<T>.distinctConsecutive() = distinctConsecutiveBy({ it }, { k, _ -> k})
//    sequence {
//        val it = iterator()
//        var prevValue: T? = null
//        while(it.hasNext()) {
//            val curVal = it.next()
//            if (curVal != prevValue) yield(curVal)
//            prevValue = curVal
//        }
//    }.toList()

fun <T, K, R> Iterable<T>.distinctConsecutiveBy(keySelector: (T) -> K, transform: (K, List<T>) -> R) : List<R> =
    sequence {
        val it = this@distinctConsecutiveBy.iterator()
        var prevKey: K? = null
        var groupList = mutableListOf<T>()

        while(it.hasNext()) {
            val curVal = it.next()
            val curKey = keySelector(curVal)
            if (prevKey != null && prevKey != curKey) {
                val folded = transform(prevKey, groupList)
                groupList = mutableListOf()
                yield(folded)
            }
            groupList += curVal
            prevKey = curKey
        }

        if (prevKey != null && groupList.isNotEmpty()) {
            val folded = transform(prevKey, groupList)
            yield(folded)
        }

    }.toList()

fun <T> Collection<T>.isDistinct() = this.size == this.toSet().size

fun <T> Iterable<IndexedValue<T>>.withoutIndex() = this.map { it.value }

fun <T: Comparable<T>> Collection<T>.isSorted(strict: Boolean = true): Boolean =
    this
        .zipWithNext()
        .all { if (strict) it.first < it.second else it.first <= it.second }

operator fun <T> List<T>.get(range: IntRange) = this.subList(range.first, range.last + 1)