package com.youcefm.bypassctrl.config

import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.youcefm.bypassctrl.BatteryMonitor
import com.youcefm.bypassctrl.databinding.ActivityGamePickerBinding

class GamePickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGamePickerBinding
    private lateinit var adapter: GameAdapter
    private var allGames = listOf<GameItem>()

    private data class PredefinedGame(
        val packageName: String,
        val name: String
    )

    private val predefinedGames = listOf(
        PredefinedGame("com.tencent.ig", "PUBG Mobile"),
        PredefinedGame("jp.konami.eFootball", "eFootball"),
        PredefinedGame("com.supercell.clashroyale", "Clash Royale"),
        PredefinedGame("com.miniclip.eightballpool", "8 Ball Pool")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGamePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val savedPackages = BatteryMonitor.readGameList().toMutableSet()
        allGames = loadGames(savedPackages)

        adapter = GameAdapter(allGames.toMutableList())
        binding.recyclerGames.layoutManager = LinearLayoutManager(this)
        binding.recyclerGames.adapter = adapter

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim().lowercase()
                val filtered = if (query.isEmpty()) {
                    allGames
                } else {
                    allGames.filter {
                        it.name.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                    }
                }
                adapter.updateList(filtered)
            }
        })

        binding.btnSave.setOnClickListener {
            val selected = adapter.getSelectedPackages()
            BatteryMonitor.saveGameList(selected)
            finish()
        }
    }

    private fun loadGames(savedPackages: MutableSet<String>): List<GameItem> {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
        val installedPackages = apps.map { it.packageName }.toSet()

        val result = mutableListOf<GameItem>()

        for (pg in predefinedGames) {
            if (pg.packageName !in installedPackages) continue
            val appInfo = apps.find { it.packageName == pg.packageName }
            val icon = appInfo?.loadIcon(pm) ?: applicationInfo.loadIcon(pm)
            val displayName = appInfo?.loadLabel(pm)?.toString() ?: pg.name
            result.add(GameItem(
                name = displayName,
                packageName = pg.packageName,
                icon = icon,
                isSelected = pg.packageName in savedPackages || savedPackages.isEmpty()
            ))
        }

        val predefinedPackages = predefinedGames.map { it.packageName }.toSet()
        val userApps = apps.filter { appInfo ->
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isOurs = appInfo.packageName == packageName
            val isPredefined = appInfo.packageName in predefinedPackages
            !isSystem && !isOurs && !isPredefined
        }

        for (appInfo in userApps) {
            result.add(GameItem(
                name = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = appInfo.loadIcon(pm),
                isSelected = appInfo.packageName in savedPackages
            ))
        }

        return result.sortedWith(compareByDescending<GameItem> { it.isSelected }.thenBy { it.name })
    }
}
