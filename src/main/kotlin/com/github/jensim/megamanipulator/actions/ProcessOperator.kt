package com.github.jensim.megamanipulator.actions

import com.github.jensim.megamanipulator.actions.apply.ApplyOutput
import com.github.jensim.megamanipulator.ui.trimProjectPath
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.future.asDeferred
import java.io.File

class ProcessOperator(private val project: Project) {

    fun runCommandAsync(workingDir: File, command: List<String>): Deferred<ApplyOutput> {
        val tempOutput = File.createTempFile("mega-manipulator-apply-out", "txt")
        val tempErrOutput = File.createTempFile("mega-manipulator-apply-err", "txt")
        val proc = ProcessBuilder(command)
            .directory(workingDir)
            .redirectError(tempOutput)
            .redirectOutput(tempOutput)
            .start()
        return proc.onExit().thenApply {
            ApplyOutput(
                std = tempOutput.readText(),
                err = tempErrOutput.readText(),
                exitCode = it.exitValue(),
                dir = workingDir.trimProjectPath(project = project),
            )
        }.asDeferred()
    }
}
