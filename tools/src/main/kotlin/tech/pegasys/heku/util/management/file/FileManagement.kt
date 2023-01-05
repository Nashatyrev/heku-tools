package tech.pegasys.heku.util.management.file

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class FileManagement<TBean>(
    private val file: File,
    private val defaultValue: TBean,
    private val serializer: (TBean) -> String,
    private val deserializer: (String) -> TBean,
    private val writeIfAbsent: Boolean = true,
    private val fileReadPeriod: Duration = 1.seconds,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val valueStateMutable = MutableStateFlow(defaultValue)
    val valueState = valueStateMutable.asStateFlow()

    init {
        if (!file.exists() && writeIfAbsent) {
            if (!file.parentFile.isDirectory && !file.parentFile.mkdirs()) {
                throw RuntimeException("Couldn't create dirs for file $file")
            }
            file.writeText(serializer(defaultValue))
        }

        scope.launch {
            while (true) {
                try {
                    val text = file.readText()
                    val freshValue = deserializer(text)
                    valueStateMutable.value = freshValue
                } catch (e: Exception) {
                    println("Error reading management file $file: $e")
                }
                delay(fileReadPeriod)
            }
        }
    }

    companion object {
        fun <TBean : Any> createJson(file: File, defaultValue: TBean): FileManagement<TBean> {
            val mapper = ObjectMapper()
            return FileManagement(
                file,
                defaultValue,
                { mapper.writeValueAsString(it) },
                { mapper.readValue(it, defaultValue::class.java) }
            )
        }
    }
}