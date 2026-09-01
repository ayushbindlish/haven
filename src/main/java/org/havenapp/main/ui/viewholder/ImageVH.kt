package org.havenapp.main.ui.viewholder

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import org.havenapp.main.R
import org.havenapp.main.model.EventTrigger
import org.havenapp.main.resources.IResourceManager

/**
 * Created by Arka Prava Basu<arkaprava94@gmail.com> on 21/02/19
 **/
class ImageVH(private val resourceManager: IResourceManager,
              private val listener: ImageClickListener, viewGroup: ViewGroup)
    : RecyclerView.ViewHolder(LayoutInflater.from(viewGroup.context)
        .inflate(R.layout.item_photo, viewGroup, false)) {

    private val indexNumber = itemView.findViewById<TextView>(R.id.index_number)
    private val imageTitle = itemView.findViewById<TextView>(R.id.title)
    private val imageDesc = itemView.findViewById<TextView>(R.id.item_camera_desc)
    private val imageView = itemView.findViewById<ImageView>(R.id.item_camera_image)

    fun bind(eventTrigger: EventTrigger, position: Int) {
        indexNumber.text = "#${position + 1}"
        imageTitle.text = eventTrigger.getStringType(resourceManager)
        imageDesc.text = org.havenapp.main.Utils.formatDateTime(eventTrigger.time)

        /**
        Uri fileUri = FileProvider.getUriForFile(
        context,
        AUTHORITY,
        new File(eventTrigger.getPath()));
        holder.image.setImageURI(fileUri);
         **/

        val viewPath = org.havenapp.main.security.MediaAccess
            .resolveForViewing(itemView.context, eventTrigger.path!!)
        Glide.with(itemView.context)
            .load(File(viewPath))
            .centerCrop()
            .into(imageView)


        imageView.setOnClickListener {
            listener.onImageClick(eventTrigger, position)
        }

        imageView.setOnLongClickListener {
            listener.onImageLongClick(eventTrigger)
            false
        }
    }

    interface ImageClickListener {
        fun onImageClick(eventTrigger: EventTrigger, position: Int)

        fun onImageLongClick(eventTrigger: EventTrigger)
    }
}
