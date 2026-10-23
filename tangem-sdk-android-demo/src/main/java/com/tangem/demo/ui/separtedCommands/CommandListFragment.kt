package com.tangem.demo.ui.separtedCommands

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import com.google.android.material.slider.Slider
import com.tangem.common.CompletionResult
import com.tangem.common.UserCodeType
import com.tangem.common.card.Card
import com.tangem.common.card.EllipticCurve
import com.tangem.common.core.Config
import com.tangem.common.core.TangemSdkError
import com.tangem.common.core.UserCodeRequestPolicy
import com.tangem.common.extensions.toHexString
import com.tangem.demo.Backup
import com.tangem.demo.postUi
import com.tangem.demo.ui.BaseFragment
import com.tangem.demo.ui.backup.BackupActivity
import com.tangem.demo.ui.extension.beginDelayedTransition
import com.tangem.demo.ui.extension.copyToClipboard
import com.tangem.demo.ui.extension.fitChipsByGroupWidth
import com.tangem.demo.ui.extension.setTextFromClipboard
import com.tangem.demo.ui.separtedCommands.task.MultiMessageTask
import com.tangem.demo.ui.separtedCommands.task.ResetToFactorySettingsTask
import com.tangem.operations.GetEntropyCommand
import com.tangem.operations.attestation.AttestationTask
import com.tangem.operations.files.FileVisibility
import com.tangem.operations.usersetttings.SetUserCodeRecoveryAllowedTask
import com.tangem.tangem_demo.R
import com.tangem.tangem_demo.databinding.FgCommandListBinding

/**
 * Created by Anton Zhilenkov on 23/12/2020.
 */
class CommandListFragment : BaseFragment() {

    private var jsonRpcSingleCommandTemplate: String = """
    {
        "jsonrpc": "2.0",
        "id": 2,
        "method": "scan",
        "params": {}
    }
    """.trim()

    private var jsonRpcListCommandsTemplate: String = """
    [
        {
          "method": "scan",
          "params": {},
          "id": 1,
          "jsonrpc": "2.0"
        },
        {
          "method": "create_wallet",
          "params": {
            "curve": "Secp256k1"
          },
          "jsonrpc": "2.0"
        },
        {
          "method": "scan",
          "id": 2,
          "jsonrpc": "2.0"
        }
    ]
    """.trim()

    private var _binding: FgCommandListBinding? = null
    private val binding get() = _binding!!

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private val sliderTouchListener = object : Slider.OnSliderTouchListener {
        @SuppressLint("RestrictedApi")
        override fun onStartTrackingTouch(slider: Slider) {
        }

        @SuppressLint("RestrictedApi")
        override fun onStopTrackingTouch(slider: Slider) {
            selectedIndexOfWallet = slider.value.toInt()
            updateWalletInfo()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = FgCommandListBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.layoutCard.chipGroupUserCodeRequestPolicy.fitChipsByGroupWidth()
        binding.layoutCard.chipGroupUserCodeRequestPolicy.setOnCheckedChangeListener { _, checkedId ->
            val showTypeSelector = checkedId == R.id.chipPolicyAlways ||
                checkedId == R.id.chipPolicyAlwaysWithBiometrics
            binding.layoutCard.chipGroupUserCodeType.isVisible = showTypeSelector
            binding.layoutCard.userCodeRequestPolicyDivider.isVisible = showTypeSelector
        }
        binding.layoutCard.btnScanCard.setOnClickListener {
            val type = when (binding.layoutCard.chipGroupUserCodeType.checkedChipId) {
                R.id.chipTypeAccessCode -> UserCodeType.AccessCode
                R.id.chipTypePasscode -> UserCodeType.Passcode
                else -> UserCodeType.AccessCode
            }
            val policy = when (binding.layoutCard.chipGroupUserCodeRequestPolicy.checkedChipId) {
                R.id.chipPolicyDefault -> UserCodeRequestPolicy.Default
                R.id.chipPolicyAlways -> UserCodeRequestPolicy.Always(type)
                R.id.chipPolicyAlwaysWithBiometrics -> UserCodeRequestPolicy.AlwaysWithBiometrics(type)
                else -> Config().userCodeRequestPolicy
            }
            scanCard(policy)
        }
        binding.layoutCard.btnLoadCardInfo.setOnClickListener { loadCardInfo() }
        binding.layoutCard.btnResetAccessCode.setOnClickListener { restoreAccessCode() }

        binding.layoutBackup.btnPersonalizePrimary.setOnClickListener { personalize(Backup.primaryCardConfig()) }
        binding.layoutBackup.btnPersonalizeBackup1.setOnClickListener { personalize(Backup.backup1Config()) }
        binding.layoutBackup.btnPersonalizeBackup2.setOnClickListener { personalize(Backup.backup2Config()) }
        binding.layoutBackup.btnDepersonalize.setOnClickListener { depersonalize() }

        binding.layoutBackup.btnStartBackup.setOnClickListener {
            val intent = Intent(requireContext(), BackupActivity::class.java)
            startActivity(intent)
        }

        binding.layoutAttestation.chipGroupAttest.fitChipsByGroupWidth()
        binding.layoutAttestation.btnAttest.setOnClickListener {
            val mode = when (binding.layoutAttestation.chipGroupAttest.checkedChipId) {
                R.id.chipAttestOffline -> AttestationTask.Mode.Offline
                R.id.chipAttestNormal -> AttestationTask.Mode.Normal
                else -> AttestationTask.Mode.Full
            }
            attest(mode)
        }
        binding.layoutAttestation.btnAttestCardKey.setOnClickListener { attestCardKey() }

        val adapter = ArrayAdapter(
            view.context,
            android.R.layout.simple_dropdown_item_1line,
            listOf(
                "m/0/1",
                "m/0'/1'/2",
                "m/44'/0'/0'/1/0",
            ),
        )
        binding.layoutHdWallet.etDerivePublicKey.setAdapter(adapter)
        binding.layoutHdWallet.etDerivePublicKey.addTextChangedListener { derivationPath = if (it!!.isEmpty()) null else it.toString() }
        binding.layoutHdWallet.btnDerivePublicKey.setOnClickListener { derivePublicKey() }

        binding.layoutSign.btnPasteHashes.setOnClickListener { binding.layoutSign.etHashesToSign.setTextFromClipboard() }
        binding.layoutSign.btnSignHash.setOnClickListener { sign(SignStrategyType.SINGLE) }
        binding.layoutSign.btnSignHashes.setOnClickListener { sign(SignStrategyType.MULTIPLE) }

        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            EllipticCurve.values(),
        )
        binding.layoutWallet.spinnerCurves.adapter = spinnerAdapter
        binding.layoutWallet.btnCreateWallet.setOnClickListener {
            createOrImportWallet(
                binding.layoutWallet.spinnerCurves.selectedItem as EllipticCurve,
                binding.layoutWallet.etMnemonic.text?.toString(),
            )
        }
        binding.layoutWallet.btnPasteMnemonic.setOnClickListener { binding.layoutWallet.etMnemonic.setTextFromClipboard() }

        binding.layoutWallet.btnPurgeWallet.setOnClickListener { purgeWallet() }
        binding.layoutWallet.btnPurgeAllWallet.setOnClickListener { purgeAllWallet() }

        binding.layoutIssuerData.btnReadIssuerData.setOnClickListener { readIssuerData() }
        binding.layoutIssuerData.btnWriteIssuerData.setOnClickListener { writeIssuerData() }

        binding.layoutIssuerExData.btnReadIssuerExData.setOnClickListener { readIssuerExtraData() }
        binding.layoutIssuerExData.btnWriteIssuerExData.setOnClickListener { writeIssuerExtraData() }

        binding.layoutUserData.btnReadUserData.setOnClickListener { readUserData() }
        binding.layoutUserData.btnWriteUserData.setOnClickListener { writeUserData() }
        binding.layoutUserData.btnWriteUserProtectedData.setOnClickListener { writeUserProtectedData() }

        binding.layoutSetPin.btnSetAccessCode.setOnClickListener { setAccessCode() }
        binding.layoutSetPin.btnSetPasscode.setOnClickListener { setPasscode() }
        binding.layoutSetPin.btnResetUserCodes.setOnClickListener { resetUserCodes() }
        binding.layoutSetPin.btnClearUserCodes.setOnClickListener {
            clearUserCodes()
            binding.layoutSetPin.userCodeRepositoryContainer.isVisible = hasSavedUserCodes()
        }
        binding.layoutSetPin.btnDeleteUserCode.setOnClickListener {
            deleteUserCodeForScannedCard()
            if (hasSavedUserCodes()) {
                binding.layoutSetPin.btnDeleteUserCode.isVisible = hasSavedUserCodeForScannedCard()
            } else {
                binding.layoutSetPin.userCodeRepositoryContainer.isVisible = false
            }
        }

        binding.layoutFileData.btnReadAllFiles.setOnClickListener { readFiles(true) }
        binding.layoutFileData.btnReadPublicFiles.setOnClickListener { readFiles(false) }
        binding.layoutFileData.btnWriteUserFile.setOnClickListener { writeUserFile() }
        binding.layoutFileData.btnWriteOwnerFile.setOnClickListener { writeOwnerFile() }
        binding.layoutFileData.btnDeleteAll.setOnClickListener { deleteFiles() }
        binding.layoutFileData.btnDeleteFirst.setOnClickListener { deleteFiles(listOf(0)) }
        binding.layoutFileData.btnMakeFilePublic.setOnClickListener { changeFilesSettings(mapOf(0 to FileVisibility.Public)) }
        binding.layoutFileData.btnMakeFilePrivate.setOnClickListener { changeFilesSettings(mapOf(0 to FileVisibility.Private)) }

        binding.layoutJsonRpc.etJsonRpc.setText(jsonRpcSingleCommandTemplate)
        binding.layoutJsonRpc.btnSingleJsonRpc.setOnClickListener { binding.layoutJsonRpc.etJsonRpc.setText(jsonRpcSingleCommandTemplate) }
        binding.layoutJsonRpc.btnListJsonRpc.setOnClickListener { binding.layoutJsonRpc.etJsonRpc.setText(jsonRpcListCommandsTemplate) }
        binding.layoutJsonRpc.btnPasteJsonRpc.setOnClickListener { binding.layoutJsonRpc.etJsonRpc.setTextFromClipboard() }
        binding.layoutJsonRpc.btnLaunchJsonRpc.setOnClickListener { launchJSONRPC(binding.layoutJsonRpc.etJsonRpc.text.toString().trim()) }

        binding.layoutUtils.btnResetToFactory.setOnClickListener {
            sdk.startSessionWithRunnable(ResetToFactorySettingsTask()) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.layoutUtils.btnGetEntropy.setOnClickListener {
            sdk.startSessionWithRunnable(GetEntropyCommand()) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.layoutUtils.chipGroupUserCodeRecoveryAllowed.fitChipsByGroupWidth()
        binding.layoutUtils.btnUserCodeRecovery.setOnClickListener {
            val allow = when (binding.layoutUtils.chipGroupUserCodeRecoveryAllowed.checkedChipId) {
                R.id.chipUserCodeRecoveryEnable -> true
                R.id.chipUserCodeRecoveryDisable -> false
                else -> false
            }
            sdk.startSessionWithRunnable(SetUserCodeRecoveryAllowedTask(allow)) {
                postUi { handleCommandResult(it) }
            }
        }
        binding.layoutUtils.btnCheckSetMessage.setOnClickListener {
            sdk.startSessionWithRunnable(
                runnable = MultiMessageTask(),
                cardId = card?.cardId,
                initialMessage = initialMessage,
            ) { postUi { handleCommandResult(it) } }
        }

        binding.layoutTheme.chipGroupTheme.fitChipsByGroupWidth()
        binding.layoutTheme.chipGroupTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.chipThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.chipThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                R.id.chipThemeSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                else -> null
            }

            if (mode != null) {
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }

        binding.layoutCard.sliderWallet.stepSize = 1f
        binding.layoutCard.tvWalletPubKey.setOnClickListener {
            requireContext().copyToClipboard(binding.layoutCard.tvWalletPubKey.text)
            showToast("PubKey copied to clipboard")
        }
    }

    private fun sign(strategyType: SignStrategyType) {
        val userHexHash = binding.layoutSign.etHashesToSign.text.toString()
        val strategy = when (strategyType) {
            SignStrategyType.SINGLE -> {
                SingleSignStrategy(userHexHash, ::signHash, ::prepareHashesToSign)
            }
            SignStrategyType.MULTIPLE -> {
                MultiplySignStrategy(userHexHash, ::signHashes, ::prepareHashesToSign)
            }
        }
        strategy.onError = { showToast(it) }
        strategy.execute()
    }

    override fun handleCommandResult(result: CompletionResult<*>) {
        when (result) {
            is CompletionResult.Success -> {
                val json = jsonConverter.prettyPrint(result.data)
                showDialog(json)

                if (hasSavedUserCodes()) {
                    binding.layoutSetPin.userCodeRepositoryContainer.isVisible = true
                    binding.layoutSetPin.btnDeleteUserCode.isVisible = hasSavedUserCodeForScannedCard()
                } else {
                    binding.layoutSetPin.userCodeRepositoryContainer.isVisible = false
                }
            }
            is CompletionResult.Failure -> {
                if (result.error is TangemSdkError.UserCancelled) {
                    showToast("${result.error.customMessage}: User was cancelled the operation")
                } else {
                    showToast(result.error.customMessage)
                }
            }
        }
    }

    override fun onCardChanged(card: Card?) {
        postUi { updateWalletsSlider() }
    }

    private fun updateWalletsSlider() {
        fun updateWalletInfoContainerVisibility(visibility: Int) {
            if (visibility == View.VISIBLE) binding.layoutCard.flCardContainer.beginDelayedTransition()
            binding.layoutCard.walletInfoContainer.visibility = visibility
        }
        binding.layoutCard.sliderWallet.removeOnSliderTouchListener(sliderTouchListener)

        if (walletsCount == 0) {
            updateWalletInfoContainerVisibility(View.GONE)
            selectedIndexOfWallet = -1
            return
        }

        binding.layoutCard.sliderWallet.post {
            if (walletsCount == 1) {
                selectedIndexOfWallet = 0
                updateWalletInfoContainerVisibility(View.VISIBLE)
                binding.layoutCard.sliderWallet.value = 0f
                binding.layoutCard.sliderWallet.valueTo = 1f
                binding.layoutCard.sliderWallet.isEnabled = false
                updateWalletInfo()
                return@post
            }

            updateWalletInfoContainerVisibility(View.VISIBLE)
            binding.layoutCard.sliderWallet.isEnabled = true
            binding.layoutCard.sliderWallet.valueFrom = 0f
            binding.layoutCard.sliderWallet.valueTo = walletsCount - 1f

            if (selectedIndexOfWallet == -1 || selectedIndexOfWallet >= walletsCount) {
                selectedIndexOfWallet = 0
            }
            binding.layoutCard.sliderWallet.value = selectedIndexOfWallet.toFloat()
            binding.layoutCard.sliderWallet.addOnSliderTouchListener(sliderTouchListener)
            updateWalletInfo()
        }
    }

    private fun updateWalletInfo() {
        binding.layoutCard.tvWalletsCount.text = "$walletsCount"
        binding.layoutCard.tvWalletIndex.text = "$selectedIndexOfWallet"
        binding.layoutCard.tvWalletCurve.text = "${selectedWallet?.curve}"
        binding.layoutCard.tvWalletPubKey.text = "${selectedWallet?.publicKey?.toHexString()}"
    }
}
