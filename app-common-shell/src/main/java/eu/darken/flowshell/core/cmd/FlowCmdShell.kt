package eu.darken.flowshell.core.cmd

import eu.darken.flowshell.core.FlowShell
import eu.darken.flowshell.core.FlowShellDebug
import eu.darken.flowshell.core.FlowShellDebug.isDebug
import eu.darken.flowshell.core.process.FlowProcess
import eu.darken.sdmse.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.sdmse.common.debug.logging.Logging.Priority.WARN
import eu.darken.sdmse.common.debug.logging.asLog
import eu.darken.sdmse.common.debug.logging.log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

class FlowCmdShell(
    flowShell: FlowShell
) {

    constructor(shell: String = "sh") : this(FlowShell(shell))

    private val sessionProducer = flowShell.session
        .onStart { if (isDebug) log(TAG, VERBOSE) { "Starting session..." } }
        .map { shellSession ->
            if (isDebug) log(TAG, VERBOSE) { "Wrapping to command shell session..." }
            Session(session = shellSession)
        }
        .onEach { if (isDebug) log(TAG, VERBOSE) { "Emitting $it" } }
        .onCompletion {
            if (isDebug) {
                if (it == null || it is CancellationException) {
                    log(TAG, VERBOSE) { "Flow is complete. (reason=$it)" }
                } else {
                    log(TAG, WARN) { "Flow is completed unexpectedly: ${it.asLog()}" }
                }
            }
        }

    val session: Flow<Session> = sessionProducer

    data class Session(
        private val session: FlowShell.Session,
    ) {
        private val _tag = "${TAG}:${session.session.id}"
        private val scope = CoroutineScope(Job() + Dispatchers.IO)
        private val mutex = Mutex()

        private var cmdCount = 0
        val counter: Int
            get() = cmdCount

        suspend fun isAlive() = session.isAlive()

        suspend fun waitFor() = session.waitFor()

        suspend fun cancel() = withContext(Dispatchers.IO) {
            if (isDebug) log(_tag) { "kill()" }
            session.cancel()
            scope.cancel()
        }

        suspend fun close() = withContext(Dispatchers.IO) {
            if (isDebug) log(_tag) { "close()" }
            session.close()
            scope.cancel()
        }

        private val sharedOutput = session.output.shareIn(scope, started = SharingStarted.Eagerly)
        private val sharedErrors = session.error.shareIn(scope, started = SharingStarted.Eagerly)

        suspend fun execute(cmd: FlowCmd): FlowCmd.Result = withContext(Dispatchers.IO) {
            mutex.withLock {
                cmdCount++
                val id = UUID.randomUUID().toString()
                val idStart = "$id-start"
                val idEnd = "$id-end"
                log(_tag, VERBOSE) { "submit($cmdCount): $cmd" }

                val output = mutableListOf<String>()
                val outputReady = CompletableDeferred<Unit>()
                val outputJob = sharedOutput
                    .onSubscription {
                        outputReady.complete(Unit)
                        if (isDebug) log(_tag, VERBOSE) { "Output monitor started ($id)" }
                    }
                    .dropWhile { it != idStart }.drop(1)
                    .onEach {
                        if (isDebug) log(_tag, VERBOSE) { "Adding (output-$id) $it" }
                        output.add(it)
                    }
                    .takeWhile { !it.startsWith(idEnd) }
                    .onCompletion { if (isDebug) log(_tag, VERBOSE) { "Output monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                val errors = mutableListOf<String>()
                val errorReady = CompletableDeferred<Unit>()
                val errorJob = sharedErrors
                    .onSubscription {
                        errorReady.complete(Unit)
                        if (isDebug) log(_tag, VERBOSE) { "Error monitor started ($id)" }
                    }
                    .dropWhile { it != idStart }.drop(1)
                    .takeWhile { it != idEnd }
                    .onEach {
                        if (isDebug) log(_tag, VERBOSE) { "Adding (errors-$id) $it" }
                        errors.add(it)
                    }
                    .onCompletion { if (isDebug) log(_tag, VERBOSE) { "Error monitor finished ($id)" } }
                    .launchIn(this + Dispatchers.IO)

                listOf(outputReady, errorReady).awaitAll()

                if (isDebug) log(_tag, VERBOSE) { "Harvesters are ready, writing commands... ($id)" }

                session.write("echo $idStart", false)
                session.write("echo $idStart >&2", false)
                cmd.instructions.forEach { session.write(it, flush = false) }
                session.write("echo $idEnd $?", false)
                session.write("echo $idEnd >&2", true)

                if (isDebug) log(_tag, VERBOSE) { "Commands are written, waiting... ($id)" }

                listOf(outputJob, errorJob).joinAll()

                if (isDebug) log(_tag, VERBOSE) { "Determining exitcode ($id)" }
                val rawExitCodeRow = output.removeAt(output.lastIndex)

                val exitCode = rawExitCodeRow
                    .split(" ")
                    .let { it[1].toIntOrNull() }
                    ?.let { FlowProcess.ExitCode(it) }
                    ?: throw IllegalArgumentException("Failed to determine exitcode from $rawExitCodeRow")

                FlowCmd.Result(
                    original = cmd,
                    exitCode = exitCode,
                    output = output,
                    errors = errors
                ).also { log(_tag) { "submit($cmdCount): $cmd -> $it" } }
            }
        }
    }

    companion object {
        private val TAG = "${FlowShellDebug.tag}:FlowCmdShell"
    }
}