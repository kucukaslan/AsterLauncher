package com.series.aster.launcher.ui.bottomsheetdialog

import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.series.aster.launcher.R
import com.series.aster.launcher.data.entities.AppInfo
import com.series.aster.launcher.databinding.BottomsheetDialogBinding
import com.series.aster.launcher.helper.AppHelper
import com.series.aster.launcher.helper.BottomDialogHelper
import com.series.aster.launcher.helper.FingerprintHelper
import com.series.aster.launcher.listener.OnItemClickedListener
import com.series.aster.launcher.viewmodel.AppViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import android.content.pm.ApplicationInfo

@AndroidEntryPoint
class AppInfoBottomSheetFragment(private val appInfo: AppInfo) : BottomSheetDialogFragment(),
    FingerprintHelper.Callback {

    private var _binding: BottomsheetDialogBinding? = null

    private val binding get() = _binding!!

    private val viewModel: AppViewModel by viewModels()

    @Inject
    lateinit var appHelper: AppHelper

    @Inject
    lateinit var bottomDialogHelper: BottomDialogHelper

    @Inject
    lateinit var fingerHelper: FingerprintHelper

    private var appStateClickListener: OnItemClickedListener.OnAppStateClickListener? = null

    private var dismissListener: OnItemClickedListener.BottomSheetDismissListener? = null

    fun setOnAppStateClickListener(listener: OnItemClickedListener.OnAppStateClickListener) {
        appStateClickListener = listener
    }

    fun setOnBottomSheetDismissedListener(listener: OnItemClickedListener.BottomSheetDismissListener) {
        dismissListener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomsheetDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initView()
        observeClickListener()
    }

    private fun initView() {
        bottomDialogHelper.setupDialogStyle(dialog)

        binding.run {
            bottomSheetFavHidden.text = getString(if (!appInfo.favorite) R.string.bottom_dialog_add_to_home else R.string.bottom_dialog_remove_from_home)
            bottomSheetHidden.text = getString(if (!appInfo.hidden) R.string.bottom_dialog_add_to_hidden else R.string.bottom_dialog_remove_to_hidden)
            bottomSheetLock.text = getString(if (!appInfo.lock) R.string.bottom_dialog_add_to_lock else R.string.bottom_dialog_remove_to_unlock)
            bottomSheetRename.setText(appInfo.appName)
            bottomSheetOrder.text = appInfo.appOrder.toString()
        }

    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeClickListener() {
        val packageManager = context?.packageManager
        val applicationInfo = packageManager?.getApplicationInfo(appInfo.packageName, 0)
        val appName = applicationInfo?.let { appHelper.getAppLabel(requireContext(), it) }

        binding.bottomSheetFavHidden.setOnClickListener {
            appStateClickListener?.onAppStateClicked(appInfo)

            appInfo.favorite = !appInfo.favorite

            viewModel.updateAppInfoFavorite(appInfo)

            Log.d("Tag", "${appInfo.appName} : Bottom Favorite: ${appInfo.favorite}")
            Log.d("Tag", "${appInfo.appName} : Bottom Order: ${appInfo.appOrder}")

            dismiss()
        }

        binding.bottomSheetRename.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Operations before text changes
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Operations when text changes
                appInfo.appName = s.toString()

                if (s.isNullOrEmpty()) {
                    viewModel.updateAppInfoAppName(appInfo, appName.toString())
                } else {
                    viewModel.updateAppInfoAppName(appInfo, s.toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Operations after text changes
                if (s.isNullOrEmpty()) {
                    binding.bottomSheetRename.hint = appName
                    appInfo.appName = appName ?: ""
                } else {
                    appInfo.appName = s.toString()
                }

            }
        })

        binding.bottomSheetRenameDone.setOnClickListener {
            appStateClickListener?.onAppStateClicked(appInfo)
            viewModel.updateAppInfoAppName(appInfo, appInfo.appName) // 更新收藏狀態和排序
            dismiss()
            Log.d("Tag", "${appInfo.appName} Bottom State: ${appInfo.appName}")
        }

        binding.bottomSheetHidden.setOnClickListener {
            appStateClickListener?.onAppStateClicked(appInfo)
            appInfo.hidden = !appInfo.hidden

            viewModel.updateAppHidden(appInfo, appInfo.hidden)
            dismiss()
        }

        binding.bottomSheetLock.setOnClickListener {
            if (appInfo.lock) {
                fingerHelper.startFingerprintAuth(appInfo, this)
            } else {
                appInfo.lock = true
                viewModel.updateAppLock(appInfo, appInfo.lock)
                dismiss()
            }
        }

        binding.bottomSheetUninstall.setOnClickListener {
            appStateClickListener?.onAppStateClicked(appInfo)
            appHelper.unInstallApp(requireContext(), appInfo)
            dismiss()
        }

        binding.bottomSheetInfo.setOnClickListener {
            appHelper.appInfo(requireContext(), appInfo)
            dismiss()
        }
    }

    override fun onAuthenticationSucceeded(appInfo: AppInfo) {
        appInfo.lock = false

        viewModel.updateAppLock(appInfo, appInfo.lock)
        dismiss()

        appHelper.showToast(requireContext(), getString(R.string.authentication_succeeded))
    }

    override fun onAuthenticationFailed() {
        appHelper.showToast(requireContext(), getString(R.string.authentication_failed))
    }

    override fun onAuthenticationError(errorCode: Int, errorMessage: CharSequence?) {
        appHelper.showToast(requireContext(), getString(R.string.authentication_error))
    }
}