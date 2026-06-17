package com.youcefm.bypassctrl.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.ShellExecutor
import com.youcefm.bypassctrl.databinding.FragmentLogBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClearLog()
        startLogPolling()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupClearLog() {
        binding.btnClearLog.setOnClickListener {
            ShellExecutor.exec("> /data/bypass/daemon.log")
            binding.tvLog.text = "Log cleared."
        }
    }

    private fun startLogPolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    val logs = withContext(Dispatchers.IO) { BatteryMonitor.readLogs() }
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        binding.tvLog.text = logs.ifEmpty { "No logs available. Is the daemon running?" }
                        binding.scrollView.post {
                            binding.scrollView.fullScroll(NestedScrollView.FOCUS_DOWN)
                        }
                    }
                    delay(2000)
                }
            }
        }
    }
}
