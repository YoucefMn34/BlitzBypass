package com.youcefm.bypassctrl.bypass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.databinding.FragmentBypassBinding

class BypassFragment : Fragment() {

    private var _binding: FragmentBypassBinding? = null
    private val binding get() = _binding!!
    private val prefs by lazy { requireContext().getSharedPreferences("blitz_prefs", android.content.Context.MODE_PRIVATE) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBypassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupThresholdSlider()
        setupTempThresholdSlider()
        setupGameModeSwitch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupThresholdSlider() {
        val enabled = prefs.getBoolean("threshold_enabled", false)
        val value = prefs.getInt("threshold_value", 80)

        binding.switchThreshold.isChecked = enabled
        binding.sliderThreshold.value = value.toFloat()
        binding.tvThresholdValue.text = "$value%"

        BatteryMonitor.setThreshold(enabled, value)

        binding.sliderThreshold.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvThresholdValue.text = "$intVal%"
            prefs.edit().putInt("threshold_value", intVal).apply()
            BatteryMonitor.setThreshold(binding.switchThreshold.isChecked, intVal)
        }

        binding.switchThreshold.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("threshold_enabled", isChecked).apply()
            binding.sliderThreshold.isEnabled = isChecked
            binding.tvThresholdValue.alpha = if (isChecked) 1.0f else 0.5f
            val currentValue = prefs.getInt("threshold_value", 80)
            BatteryMonitor.setThreshold(isChecked, currentValue)
        }

        binding.sliderThreshold.isEnabled = enabled
        binding.tvThresholdValue.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun setupTempThresholdSlider() {
        val enabled = prefs.getBoolean("temp_threshold_enabled", false)
        val trigger = prefs.getInt("temp_trigger_value", 40)
        val resume = prefs.getInt("temp_resume_value", 38)

        binding.switchTempThreshold.isChecked = enabled
        binding.sliderTempTrigger.value = trigger.toFloat()
        binding.sliderTempResume.value = resume.toFloat()
        binding.tvTempTriggerValue.text = "${trigger}°C"
        binding.tvTempResumeValue.text = "${resume}°C"

        BatteryMonitor.setTempThreshold(enabled, trigger, resume)

        binding.sliderTempTrigger.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvTempTriggerValue.text = "${intVal}°C"
            prefs.edit().putInt("temp_trigger_value", intVal).apply()

            val resumeVal = binding.sliderTempResume.value.toInt()
            if (intVal < resumeVal + 2) {
                binding.sliderTempResume.value = (intVal - 2).coerceAtLeast(30).toFloat()
                binding.tvTempResumeValue.text = "${binding.sliderTempResume.value.toInt()}°C"
            }

            BatteryMonitor.setTempThreshold(
                binding.switchTempThreshold.isChecked,
                binding.sliderTempTrigger.value.toInt(),
                binding.sliderTempResume.value.toInt()
            )
        }

        binding.sliderTempResume.addOnChangeListener { _, v, _ ->
            val intVal = v.toInt()
            binding.tvTempResumeValue.text = "${intVal}°C"
            prefs.edit().putInt("temp_resume_value", intVal).apply()

            val triggerVal = binding.sliderTempTrigger.value.toInt()
            if (intVal > triggerVal - 2) {
                binding.sliderTempTrigger.value = (intVal + 2).coerceAtMost(60).toFloat()
                binding.tvTempTriggerValue.text = "${binding.sliderTempTrigger.value.toInt()}°C"
            }

            BatteryMonitor.setTempThreshold(
                binding.switchTempThreshold.isChecked,
                binding.sliderTempTrigger.value.toInt(),
                binding.sliderTempResume.value.toInt()
            )
        }

        binding.switchTempThreshold.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("temp_threshold_enabled", isChecked).apply()
            binding.sliderTempTrigger.isEnabled = isChecked
            binding.sliderTempResume.isEnabled = isChecked
            binding.tvTempTriggerValue.alpha = if (isChecked) 1.0f else 0.5f
            binding.tvTempResumeValue.alpha = if (isChecked) 1.0f else 0.5f
            val currentTrigger = prefs.getInt("temp_trigger_value", 40)
            val currentResume = prefs.getInt("temp_resume_value", 38)
            BatteryMonitor.setTempThreshold(isChecked, currentTrigger, currentResume)
        }

        binding.sliderTempTrigger.isEnabled = enabled
        binding.sliderTempResume.isEnabled = enabled
        binding.tvTempTriggerValue.alpha = if (enabled) 1.0f else 0.5f
        binding.tvTempResumeValue.alpha = if (enabled) 1.0f else 0.5f
    }

    private fun setupGameModeSwitch() {
        val enabled = BatteryMonitor.readGameMode()
        binding.switchGameMode.isChecked = enabled

        binding.switchGameMode.setOnCheckedChangeListener { _, isChecked ->
            BatteryMonitor.setGameMode(isChecked)
        }
    }
}
