package com.youcefm.bypassctrl.config

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.BuildConfig
import com.youcefm.bypassctrl.R
import com.youcefm.bypassctrl.ShellExecutor
import com.youcefm.bypassctrl.databinding.FragmentConfigBinding

class ConfigFragment : Fragment() {

    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    private val prefs by lazy { requireContext().getSharedPreferences("blitz_prefs", Context.MODE_PRIVATE) }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updateNotifStatus(granted)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAutoOpenSwitch()
        setupNotifPermission()
        setupAbout()
        setupRestartDaemon()
        setupGameList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupAutoOpenSwitch() {
        val enabled = BatteryMonitor.readAutoOpen()
        binding.switchAutoOpen.isChecked = enabled

        binding.switchAutoOpen.setOnCheckedChangeListener { _, isChecked ->
            BatteryMonitor.setAutoOpen(isChecked)
        }
    }

    private fun setupNotifPermission() {
        updateNotifStatus(isNotificationPermissionGranted())

        binding.btnNotifPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!isNotificationPermissionGranted()) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val intent = android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
                    val intentExtra = android.provider.Settings.EXTRA_APP_PACKAGE
                    startActivity(Intent(intent).putExtra(intentExtra, requireContext().packageName))
                }
            } else {
                Toast.makeText(requireContext(), "Notification permission is auto-granted on this version", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun updateNotifStatus(granted: Boolean) {
        if (_binding == null) return
        if (granted) {
            binding.tvNotifStatus.text = "Granted"
            binding.tvNotifStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.charge_active))
            binding.btnNotifPermission.text = "Settings"
        } else {
            binding.tvNotifStatus.text = "Required for bypass alerts"
            binding.tvNotifStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.md_theme_onSurfaceVariant))
            binding.btnNotifPermission.text = "Grant"
        }
    }

    private fun setupAbout() {
        binding.tvVersion.text = BuildConfig.VERSION_NAME
        refreshDaemonStatus()
    }

    private fun setupRestartDaemon() {
        binding.btnRestartDaemon.setOnClickListener {
            ShellExecutor.exec("killall bypass-daemon.sh 2>/dev/null")
            Thread.sleep(500)
            ShellExecutor.exec("nohup /system/bin/bypass-daemon.sh > /dev/null 2>&1 &")
            Thread.sleep(1000)
            refreshDaemonStatus()
            Toast.makeText(requireContext(), "Daemon restarted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshDaemonStatus() {
        if (_binding == null) return
        val daemonRunning = ShellExecutor.exec("pgrep -f bypass-daemon.sh 2>/dev/null || echo 0").trim() != "0"
        if (daemonRunning) {
            binding.tvDaemonStatus.text = "Running"
            binding.tvDaemonStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.charge_active))
        } else {
            binding.tvDaemonStatus.text = "Not running"
            binding.tvDaemonStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.bypass_active))
        }
    }

    private fun setupGameList() {
        refreshGameCount()
        binding.root.findViewById<View>(R.id.tvGameCount)?.parent?.let { parent ->
            (parent.parent as? View)?.setOnClickListener {
                startActivity(Intent(requireContext(), GamePickerActivity::class.java))
            }
        }
    }

    private fun refreshGameCount() {
        val count = BatteryMonitor.readGameList().size
        if (_binding == null) return
        binding.tvGameCount.text = if (count > 0) "$count game(s) selected" else "Select games for Game Mode"
    }

    override fun onResume() {
        super.onResume()
        refreshGameCount()
    }
}
