package io.github.shawlaw.liblintcheck

import com.android.tools.lint.detector.api.*
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.io.File

@Suppress("UnstableApiUsage")
class IllegalCheckClassScanner: Detector(), ClassScanner, SourceCodeScanner {

    companion object {
        private const val METHOD_CFG_NAME = "lint_illegal_methods.cfg"
        private const val FIELD_CFG_NAME = "lint_illegal_fields.cfg"

        @JvmStatic
        val ISSUE = Issue.create(
            id = "CustomIllegalCheck",
            briefDescription = "Check the method-invoke and field-access on demand",
            explanation = "According to the $METHOD_CFG_NAME and $FIELD_CFG_NAME file in applying module's folder to run the check",
            category = Category.SECURITY,
            priority = 10,
            severity = Severity.FATAL,
            implementation = Implementation(
                IllegalCheckClassScanner::class.java,
                Scope.ALL_CLASSES_AND_LIBRARIES
            )
        )
    }

    private val problemFunSet = mutableSetOf<String>()

    private val problemFieldSet = mutableSetOf<String>()

    private val reportIssues = mutableSetOf<String>()

    override fun beforeCheckRootProject(context: Context) {
        super.beforeCheckRootProject(context)
        val methodCfg = File(context.project.dir, METHOD_CFG_NAME)
        if (methodCfg.isFile) {
            methodCfg.readLines().forEach {
                if (!it.startsWith("#")) {
                    problemFunSet.add(it)
                }
            }
        }
        val fieldCfg = File(context.project.dir, FIELD_CFG_NAME)
        if (fieldCfg.isFile) {
            fieldCfg.readLines().forEach {
                if (!it.startsWith("#")) {
                    problemFieldSet.add(it)
                }
            }
        }
    }

    override fun getApplicableAsmNodeTypes(): IntArray {
        return intArrayOf(AbstractInsnNode.FIELD_INSN, AbstractInsnNode.METHOD_INSN)
    }

    override fun checkInstruction(
        context: ClassContext,
        classNode: ClassNode,
        method: MethodNode,
        instruction: AbstractInsnNode
    ) {
        val callerMethodSig = classNode.name.replace("/", ".") + "." + method.name + method.desc
        when(instruction.opcode) {
            Opcodes.INVOKEVIRTUAL,
            Opcodes.INVOKESPECIAL,
            Opcodes.INVOKESTATIC,
            Opcodes.INVOKEINTERFACE,
            -> {
                (instruction as? MethodInsnNode)?.apply {
                    if (problemFun(this)) {
                        val location = context.getLocation(this)
                        val message = "$callerMethodSig has invoked method ${normalizeMethodPath(this)} !"
                        val reportIssue = "$message ${location.start?.line}"
                        if (!reportIssues.contains(reportIssue)) {
                            reportIssues.add(reportIssue)
                            context.report(ISSUE, method, this, location, message)
                        }
                    }
                }
            }
            Opcodes.GETSTATIC,
            Opcodes.PUTSTATIC,
            Opcodes.GETFIELD,
            Opcodes.PUTFIELD,
            -> {
                (instruction as? FieldInsnNode)?.apply {
                    if (problemField(this)) {
                        val location = context.getLocation(this)
                        val message = "$callerMethodSig has accessed field ${normalizeFieldPath(this)} !"
                        val reportIssue = "$message ${location.start?.line}"
                        if (!reportIssues.contains(reportIssue)) {
                            reportIssues.add(reportIssue)
                            context.report(ISSUE, method, this, location, message)
                        }
                    }
                }
            }
        }
    }

    private fun problemFun(methodInsnNode: MethodInsnNode): Boolean {
        return problemFunSet.contains(normalizeMethodPath(methodInsnNode))
    }

    private fun normalizeMethodPath(methodInsnNode: MethodInsnNode): String {
        return "${methodInsnNode.owner.replace("/", ".")}.${methodInsnNode.name}"
    }


    private fun problemField(fieldInsnNode: FieldInsnNode): Boolean {
        return problemFieldSet.contains(normalizeFieldPath(fieldInsnNode))
    }

    private fun normalizeFieldPath(fieldInsnNode: FieldInsnNode): String {
        return "${fieldInsnNode.owner.replace("/", ".")}.${fieldInsnNode.name}"
    }
}