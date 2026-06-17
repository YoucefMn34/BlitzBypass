package com.youcefm.bypassctrl.optimizations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.ShellExecutor
import com.youcefm.bypassctrl.databinding.FragmentOptimizationsBinding

class OptimizationsFragment : Fragment() {

    private var _binding: FragmentOptimizationsBinding? = null
    private val binding get() = _binding!!
    private val prefs by lazy { requireContext().getSharedPreferences("blitz_prefs", android.content.Context.MODE_PRIVATE) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOptimizationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGodModeSwitch()
        setupNetworkSwitch()
        setupTouchSwitch()
        setupThermalSwitch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupGodModeSwitch() {
        val enabled = ShellExecutor.exec("cat /data/bypass/god_mode 2>/dev/null || echo 0").trim() == "1"
        binding.switchGodMode.isChecked = enabled
        updateSubSwitchesState(enabled)

        binding.switchGodMode.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Save current state of each sub-switch before enabling God Mode
                prefs.edit()
                    .putBoolean("pre_godmode_network", binding.switchNetwork.isChecked)
                    .putBoolean("pre_godmode_touch", binding.switchTouch.isChecked)
                    .putBoolean("pre_godmode_thermal", binding.switchThermal.isChecked)
                    .apply()

                BatteryMonitor.applyGodMode()
                binding.switchNetwork.isChecked = true
                binding.switchTouch.isChecked = true
                binding.switchThermal.isChecked = true
                updateSubSwitchesState(true)
                Toast.makeText(requireContext(), "GOD MODE ENABLED", Toast.LENGTH_LONG).show()
            } else {
                BatteryMonitor.revertGodMode()

                // Restore each sub-switch to its pre-God-Mode state
                val wasNetwork = prefs.getBoolean("pre_godmode_network", false)
                val wasTouch = prefs.getBoolean("pre_godmode_touch", false)
                val wasThermal = prefs.getBoolean("pre_godmode_thermal", false)

                binding.switchNetwork.isChecked = wasNetwork
                binding.switchTouch.isChecked = wasTouch
                binding.switchThermal.isChecked = wasThermal

                // Apply or revert each based on restored state
                if (wasNetwork) BatteryMonitor.applyNetworkOptimizations() else BatteryMonitor.revertNetworkOptimizations()
                if (wasTouch) BatteryMonitor.applyTouchOptimizations() else BatteryMonitor.revertTouchOptimizations()
                if (wasThermal) BatteryMonitor.applyDisableThermals() else BatteryMonitor.revertDisableThermals()

                updateSubSwitchesState(false)
                Toast.makeText(requireContext(), "God Mode disabled — previous settings restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNetworkSwitch() {
        val enabled = ShellExecutor.exec("cat /data/bypass/net_opt 2>/dev/null || echo 0").trim() == "1"
        binding.switchNetwork.isChecked = enabled

        binding.switchNetwork.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchGodMode.isChecked) return@setOnCheckedChangeListener
            if (isChecked) {
                BatteryMonitor.applyNetworkOptimizations()
                Toast.makeText(requireContext(), "Network optimizations applied", Toast.LENGTH_SHORT).show()
            } else {
                BatteryMonitor.revertNetworkOptimizations()
                Toast.makeText(requireContext(), "Network optimizations reverted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTouchSwitch() {
        val enabled = ShellExecutor.exec("cat /data/bypass/touch_opt 2>/dev/null || echo 0").trim() == "1"
        binding.switchTouch.isChecked = enabled

        binding.switchTouch.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchGodMode.isChecked) return@setOnCheckedChangeListener
            if (isChecked) {
                BatteryMonitor.applyTouchOptimizations()
                Toast.makeText(requireContext(), "Touch optimizations applied", Toast.LENGTH_SHORT).show()
            } else {
                BatteryMonitor.revertTouchOptimizations()
                Toast.makeText(requireContext(), "Touch optimizations reverted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupThermalSwitch() {
        val enabled = ShellExecutor.exec("cat /data/bypass/thermal_off 2>/dev/null || echo 0").trim() == "1"
        binding.switchThermal.isChecked = enabled

        binding.switchThermal.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchGodMode.isChecked) return@setOnCheckedChangeListener
            if (isChecked) {
                BatteryMonitor.applyDisableThermals()
                Toast.makeText(requireContext(), "Thermals disabled", Toast.LENGTH_SHORT).show()
            } else {
                BatteryMonitor.revertDisableThermals()
                Toast.makeText(requireContext(), "Thermals restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateSubSwitchesState(godModeOn: Boolean) {
        binding.switchNetwork.isEnabled = !godModeOn
        binding.switchTouch.isEnabled = !godModeOn
        binding.switchThermal.isEnabled = !godModeOn
    }
}
