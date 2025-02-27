package app.revanced.patches.tiktok.misc.settings.patch

import app.revanced.extensions.toErrorResult
import app.revanced.patcher.annotation.Description
import app.revanced.patcher.annotation.Name
import app.revanced.patcher.annotation.Version
import app.revanced.patcher.data.BytecodeContext
import app.revanced.patcher.extensions.InstructionExtensions.addInstructions
import app.revanced.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.InstructionExtensions.getInstruction
import app.revanced.patcher.patch.BytecodePatch
import app.revanced.patcher.patch.PatchResult
import app.revanced.patcher.patch.PatchResultSuccess
import app.revanced.patcher.patch.annotations.DependsOn
import app.revanced.patcher.patch.annotations.Patch
import app.revanced.patcher.util.smali.ExternalLabel
import app.revanced.patches.tiktok.misc.integrations.patch.IntegrationsPatch
import app.revanced.patches.tiktok.misc.settings.annotations.SettingsCompatibility
import app.revanced.patches.tiktok.misc.settings.fingerprints.*
import org.jf.dexlib2.Opcode
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction

@Patch
@DependsOn([IntegrationsPatch::class])
@Name("Settings")
@Description("Adds ReVanced settings to TikTok.")
@SettingsCompatibility
@Version("0.0.1")
class SettingsPatch : BytecodePatch(
    listOf(
        AdPersonalizationActivityOnCreateFingerprint,
        AddSettingsEntryFingerprint,
        SettingsEntryFingerprint,
        SettingsEntryInfoFingerprint,
    )
) {
    override fun execute(context: BytecodeContext): PatchResult {
        // Find the class name of classes which construct a settings entry
        val settingsButtonClass = SettingsEntryFingerprint.result?.classDef?.type?.toClassName()
            ?: return SettingsEntryFingerprint.toErrorResult()
        val settingsButtonInfoClass = SettingsEntryInfoFingerprint.result?.classDef?.type?.toClassName()
            ?: return SettingsEntryInfoFingerprint.toErrorResult()

        // Create a settings entry for 'revanced settings' and add it to settings fragment
        AddSettingsEntryFingerprint.result?.apply {
            scanResult.patternScanResult?.startIndex?.let {
                val settingsEntries = mutableMethod.getInstruction(it + 3)
                val addEntry = mutableMethod.getInstruction(it + 5)
                // Add the settings entry created to the settings fragment
                mutableMethod.addInstructions(
                    it + 6,
                    listOf(
                        settingsEntries,
                        addEntry
                    )
                )
                // These instructions call a method that create a settings entry use reflection base on the class name of classes that construct settings entry
                mutableMethod.addInstructions(
                    it + 6,
                    """
                        const-string v1, "$settingsButtonClass"
                        const-string v2, "$settingsButtonInfoClass"
                        invoke-static {v1, v2}, $CREATE_SETTINGS_ENTRY_METHOD_DESCRIPTOR
                        move-result-object v1
                    """
                )
            }
        } ?: return AddSettingsEntryFingerprint.toErrorResult()

        // Initialize the settings menu once the replaced setting entry is clicked.
        AdPersonalizationActivityOnCreateFingerprint.result?.mutableMethod?.apply {
            val initializeSettingsIndex = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_SUPER
            } + 1

            val thisRegister = getInstruction<FiveRegisterInstruction>(initializeSettingsIndex - 1).registerC

            addInstructionsWithLabels(
                initializeSettingsIndex,
                """
                    invoke-static {v$thisRegister}, $INITIALIZE_SETTINGS_METHOD_DESCRIPTOR
                    move-result v0
                    if-eqz v0, :notrevanced
                    return-void
                """,
                ExternalLabel("notrevanced", getInstruction(initializeSettingsIndex))
            )
        } ?: return AdPersonalizationActivityOnCreateFingerprint.toErrorResult()

        return PatchResultSuccess()
    }

    private fun String.toClassName(): String {
        return substring(1, this.length - 1).replace("/", ".")
    }

    private companion object {
        private const val INTEGRATIONS_CLASS_DESCRIPTOR =
            "Lapp/revanced/tiktok/settingsmenu/SettingsMenu;"

        private const val INITIALIZE_SETTINGS_METHOD_DESCRIPTOR =
            "$INTEGRATIONS_CLASS_DESCRIPTOR->initializeSettings(" +
                    "Lcom/bytedance/ies/ugc/aweme/commercialize/compliance/personalization/AdPersonalizationActivity;" +
                    ")Z"
        private const val CREATE_SETTINGS_ENTRY_METHOD_DESCRIPTOR =
            "$INTEGRATIONS_CLASS_DESCRIPTOR->createSettingsEntry(" +
                    "Ljava/lang/String;" +
                    "Ljava/lang/String;" +
                    ")Ljava/lang/Object;"
    }
}