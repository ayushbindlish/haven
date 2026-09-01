package org.havenapp.main.ui.viewholder

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import java.io.File
import android.view.ViewGroup
import android.widget.TextView
import android.widget.VideoView
import androidx.recyclerview.widget.RecyclerView
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import org.havenapp.main.resources.IResourceManager

/**
 * Created by Arka Prava Basu<arkaprava94@gmail.com> on 21/02/19
 **/
class VideoVH(private val clickListener: VideoClickListener, private val context: Context,
              private val resourceManager: IResourceManager, viewGroup: ViewGroup)
    : RecyclerView.ViewHolder(LayoutInflater.from(viewGroup.context)
    .inflate(R.layout.item_video, viewGroup, false)) {

    private val indexNumber = itemView.findViewById<TextView>(R.id.index_number)
    private val title = itemView.findViewById<TextView>(R.id.title)
    private val desc = itemView.findViewById<TextView>(R.id.item_video_desc)
    private val videoView = itemView.findViewById<VideoView>(R.id.item_video_view)

    fun bind(eventTrigger: EventTrigger, position: Int) {
        indexNumber.text = "#${position + 1}"
        title.text = eventTrigger.getStringType(resourceManager)
        desc.text = org.havenapp.main.Utils.formatDateTime(eventTrigger.time)

        val vpath = org.havenapp.main.security.MediaAccess
            .resolveForViewing(context, eventTrigger.path.toString())
        val thumb = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ThumbnailUtils.createVideoThumbnail(File(vpath), Size(1280, 720), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(vpath,
                    MediaStore.Video.Thumbnails.FULL_SCREEN_KIND)
            }
        } catch (e: Exception) {
            null
        }
        if (thumb != null) videoView.background = BitmapDrawable(context.resources, thumb)
        videoView.setOnClickListener {
            clickListener.onVideoClick(eventTrigger)
        }

        videoView.setOnLongClickListener {
            clickListener.onVideoLongClick(eventTrigger)
            true
        }
    }

    interface VideoClickListener {
        fun onVideoClick(eventTrigger: EventTrigger)
        fun onVideoLongClick(eventTrigger: EventTrigger)
    }
}
