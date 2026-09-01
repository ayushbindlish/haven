package org.havenapp.main.ui.viewholder

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.masoudss.lib.WaveformSeekBar
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import org.havenapp.main.resources.IResourceManager
import java.io.File

/**
 * Created by Arka Prava Basu<arkaprava94@gmail.com> on 21/02/19
 **/
class AudioVH(private val resourceManager: IResourceManager, viewGroup: ViewGroup)
    : RecyclerView.ViewHolder(LayoutInflater.from(viewGroup.context)
        .inflate(R.layout.item_audio, viewGroup, false)) {

    private val indexNumber = itemView.findViewById<TextView>(R.id.index_number)
    private val audioTitle = itemView.findViewById<TextView>(R.id.title)
    private val audioDesc = itemView.findViewById<TextView>(R.id.item_audio_desc)
    private val waveFormView = itemView.findViewById<WaveformSeekBar>(R.id.item_sound)
    private var player: org.havenapp.main.ui.AudioMiniPlayer? = null

    fun bind(eventTrigger: EventTrigger, context: Context, position: Int) {
        indexNumber.text = "#${position + 1}"
        audioTitle.text = eventTrigger.getStringType(resourceManager)
        audioDesc.text = org.havenapp.main.Utils.formatDateTime(eventTrigger.time)

        val fileSound = File(org.havenapp.main.security.MediaAccess
            .resolveForViewing(context, eventTrigger.path))
        try {
            waveFormView.setSampleFrom(fileSound)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        player?.release()
        player = org.havenapp.main.ui.AudioMiniPlayer.bind(itemView, fileSound)
    }
}
