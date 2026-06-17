package com.youcefm.bypassctrl.dashboard

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.R
import com.youcefm.bypassctrl.animateColor
import com.youcefm.bypassctrl.animateNumber
import com.youcefm.bypassctrl.databinding.FragmentDashboardBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val lastValues = mutableMapOf<Int, Float>()
    private lateinit var qsToastReceiver: BroadcastReceiver

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToggleButton()
        setupPullToRefresh()
        setupQsToastReceiver()
        startMonitoring()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { requireContext().unregisterReceiver(qsToastReceiver) } catch (_: Exception) {}
        _binding = null
    }

    private fun setupToggleButton() {
        binding.btnToggle.setOnClickListener {
            triggerHaptic()
            binding.btnToggle.isEnabled = false
            val current = binding.tvMode.text.toString()
            val targetBypass = current != "BYPASS"

            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                BatteryMonitor.setMode(targetBypass)
                delay(500)
                withContext(Dispatchers.Main) {
                    binding.btnToggle.isEnabled = true
                }
            }
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.md_theme_primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewLifecycleOwner.lifecycleScope.launch {
                refreshData()
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun setupQsToastReceiver() {
        qsToastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val mode = intent.getStringExtra("mode") ?: return
                android.widget.Toast.makeText(context, "QS Tile: Switched to $mode", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        val filter = IntentFilter("com.youcefm.bypassctrl.TOGGLE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(qsToastReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            requireContext().registerReceiver(qsToastReceiver, filter)
        }
    }

    private fun startMonitoring() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    refreshData()
                    delay(2000)
                }
            }
        }
    }

    private suspend fun refreshData() {
        val info = withContext(Dispatchers.IO) { BatteryMonitor.readInfo() }

        withContext(Dispatchers.Main) {
            if (_binding == null) return@withContext
            updateStatusUI(info)
            updateStatsUI(info)
        }
    }

    private fun updateStatusUI(info: com.youcefm.bypassctrl.BatteryInfo) {
        val isBypass = info.mode == "BYPASS"
        val isCharging = info.status.equals("Charging", ignoreCase = true)

        val targetColor = ContextCompat.getColor(
            requireContext(),
            if (isBypass) R.color.bypass_active else R.color.charge_active
        )

        if (binding.tvMode.currentTextColor != targetColor) {
            binding.tvMode.animateColor(targetColor)
        }

        val targetStroke = ContextCompat.getColor(
            requireContext(),
            if (isBypass) R.color.bypass_active else R.color.charge_active
        )
        if (binding.cardStatus.strokeColor != targetStroke) {
            val animator = ValueAnimator.ofObject(
                ArgbEvaluator(),
                binding.cardStatus.strokeColor,
                targetStroke
            )
            animator.duration = 300
            animator.addUpdateListener {
                binding.cardStatus.strokeColor = it.animatedValue as Int
            }
            animator.start()
        }

        binding.tvMode.text = info.mode
        binding.tvMode.setTextColor(targetColor)

        val btnColor = if (isBypass) {
            ContextCompat.getColor(requireContext(), R.color.bypass_active)
        } else {
            ContextCompat.getColor(requireContext(), R.color.charge_active)
        }
        binding.btnToggle.text = if (isBypass) "Switch to Charge" else "Switch to Bypass"
        binding.btnToggle.setTextColor(btnColor)
        binding.btnToggle.iconTint = android.content.res.ColorStateList.valueOf(btnColor)
        binding.btnToggle.strokeColor = android.content.res.ColorStateList.valueOf(btnColor)

        binding.batteryIcon.batteryLevel = info.levelRaw
        binding.batteryIcon.isBypass = isBypass
        binding.batteryIcon.isCharging = isCharging
        binding.batteryIcon.invalidate()

        binding.powerFlow.isBypass = isBypass
        binding.powerFlow.isPlugged = info.isPlugged
        binding.powerFlow.invalidate()
    }

    private fun updateStatsUI(info: com.youcefm.bypassctrl.BatteryInfo) {
        animateNumber(R.id.tvLevel, info.levelRaw.toFloat(), "%.0f%%", info.level)
        setText(R.id.tvStatus, info.status)
        animateNumber(R.id.tvTemp, info.tempRaw, "%.1f°C", info.temp, getTempColor(info.tempRaw))
        animateNumber(R.id.tvVoltage, info.voltageRaw, "%.3fV", info.voltage)
        animateNumber(R.id.tvCurrent, info.currentRaw, "%.0fmA", info.current)
        animateNumber(R.id.tvPower, info.powerRaw, "%.2fW", info.power)
        animateNumber(R.id.tvCC, info.ccRaw, "%.2fA", info.cc)
    }

    private fun animateNumber(viewId: Int, newRaw: Float, format: String, displayText: String, textColor: Int? = null) {
        val view = view?.findViewById<TextView>(viewId) ?: return
        val oldRaw = lastValues[viewId] ?: newRaw
        lastValues[viewId] = newRaw
        view.animateNumber(oldRaw, newRaw, format)
        textColor?.let { view.setTextColor(it) }
    }

    private fun setText(viewId: Int, text: String) {
        view?.findViewById<TextView>(viewId)?.text = text
    }

    private fun getTempColor(tempC: Float): Int {
        return when {
            tempC >= 45f -> ContextCompat.getColor(requireContext(), R.color.temp_hot)
            tempC >= 42f -> ContextCompat.getColor(requireContext(), R.color.temp_warm)
            tempC >= 37f -> ContextCompat.getColor(requireContext(), R.color.temp_mild)
            else -> ContextCompat.getColor(requireContext(), R.color.temp_cool)
        }
    }

    private fun triggerHaptic() {
        val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
}
