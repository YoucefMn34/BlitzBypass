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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOptimizationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupNetworkSwitch()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupNetworkSwitch() {
        val enabled = ShellExecutor.exec("cat /data/bypass/net_opt 2>/dev/null || echo 0").trim() == "1"
        binding.switchNetwork.isChecked = enabled

        binding.switchNetwork.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                BatteryMonitor.applyNetworkOptimizations()
                Toast.makeText(requireContext(), "Network optimizations applied", Toast.LENGTH_SHORT).show()
            } else {
                BatteryMonitor.revertNetworkOptimizations()
                Toast.makeText(requireContext(), "Network optimizations reverted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
