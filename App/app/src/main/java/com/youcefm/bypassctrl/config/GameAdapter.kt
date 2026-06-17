package com.youcefm.bypassctrl.config

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.youcefm.bypassctrl.R

class GameAdapter(private val games: MutableList<GameItem>) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.gameIcon)
        val name: TextView = view.findViewById(R.id.gameName)
        val checkBox: CheckBox = view.findViewById(R.id.gameCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_game, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.icon.setImageDrawable(game.icon)
        holder.name.text = game.name
        holder.checkBox.isChecked = game.isSelected

        holder.itemView.setOnClickListener {
            game.isSelected = !game.isSelected
            holder.checkBox.isChecked = game.isSelected
        }

        holder.checkBox.setOnClickListener {
            game.isSelected = holder.checkBox.isChecked
        }
    }

    override fun getItemCount() = games.size

    fun getSelectedPackages(): List<String> {
        return games.filter { it.isSelected }.map { it.packageName }
    }

    fun updateList(newGames: List<GameItem>) {
        games.clear()
        games.addAll(newGames)
        notifyDataSetChanged()
    }
}
