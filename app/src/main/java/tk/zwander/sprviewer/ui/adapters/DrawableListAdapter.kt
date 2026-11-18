package tk.zwander.sprviewer.ui.adapters

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.res.Resources
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.dongliu.apk.parser.ApkFile
import tk.zwander.sprviewer.R
import tk.zwander.sprviewer.data.CustomPackageInfo
import tk.zwander.sprviewer.data.UDrawableData
import tk.zwander.sprviewer.databinding.DrawableInfoLayoutBinding
import tk.zwander.sprviewer.util.getAppDrawables

class DrawableListAdapter(private val remRes: Resources, private val itemSelectedListener: (UDrawableData) -> Unit) : BaseListAdapter<UDrawableData, DrawableListAdapter.ListVH>() {
    companion object {
        private const val PREVIEW_SIZE_DP = 48
        private const val MAX_FALLBACK_BYTES = 16 * 1024 * 1024
    }

    override val viewRes = R.layout.drawable_info_layout

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ListVH, position: Int, info: UDrawableData) {
        holder.onBind(info)
    }

    override fun onCreateViewHolder(parent: ViewGroup, position: Int): ListVH {
        return ListVH(
            LayoutInflater.from(parent.context)
                .inflate(viewRes, parent, false)
        )
    }

    override fun compare(o1: UDrawableData, o2: UDrawableData): Int {
        val names = o1.name.compareTo(o2.name, true)
        val paths = o1.path.compareTo(o2.path, true)
        return if (names == 0) paths else names
    }

    override fun areContentsTheSame(oldItem: UDrawableData, newItem: UDrawableData): Boolean {
        return oldItem.path == newItem.path
    }

    override fun matches(query: String, data: UDrawableData): Boolean {
        return data.path.contains(query, true) || "${data.name}.${data.ext}".contains(query, true)
    }

    fun loadItemsAsync(
        apk: ApkFile,
        packageInfo: CustomPackageInfo,
        listener: () -> Unit,
        progressListener: (Int, Int) -> Unit
    ) = launch {
        val drawables = try {
            withContext(Dispatchers.IO) {
                getAppDrawables(apk, packageInfo) { _, size, count ->
                    launch(Dispatchers.Main) {
                        progressListener(size, count)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Includes OutOfMemoryError on huge resource tables.
            Log.e("SPRViewer", "Failed to load drawables", e)
            emptyList()
        }

        withContext(Dispatchers.Main) {
            addAll(drawables)
            listener()
        }
    }

    inner class ListVH(view: View) : BaseVH(view) {
        private val binding = DrawableInfoLayoutBinding.bind(itemView)

        @SuppressLint("SetTextI18n")
        fun onBind(info: UDrawableData) {
            itemView.apply {
                binding.extIndicator.isVisible = true
                binding.imgPreview.isVisible = true

                val previewSize = (PREVIEW_SIZE_DP * resources.displayMetrics.density).toInt()

                try {
                    Picasso.get().cancelRequest(binding.imgPreview)

                    Picasso.get().load(
                        Uri.parse(
                            "${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                    "${info.packageInfo.packageName}/" +
                                    "${remRes.getResourceTypeName(info.id)}/" +
                                    "${info.id}"
                        )
                    )
                        // Without this, Picasso decodes the full-size image even though the
                        // preview is only 48dp. Big images then crash the app with
                        // "Canvas: trying to draw too large bitmap" or OOM.
                        .resize(previewSize, previewSize)
                        .centerInside()
                        .onlyScaleDown()
                        .into(binding.imgPreview, object : Callback {
                            override fun onError(e: Exception?) {
                                try {
                                    val fallback = ResourcesCompat.getDrawable(remRes, info.id, remRes.newTheme())

                                    // Don't hand a gigantic bitmap to the RecyclerView; drawing it
                                    // throws "trying to draw too large bitmap".
                                    if (fallback is BitmapDrawable &&
                                        (fallback.bitmap?.allocationByteCount ?: 0) > MAX_FALLBACK_BYTES) {
                                        binding.imgPreview.isVisible = false
                                    } else {
                                        binding.imgPreview.setImageDrawable(fallback)
                                        binding.extIndicator.isVisible = false
                                    }
                                } catch (t: Throwable) {
                                    // Catch Throwable: broken/huge drawables can throw Errors
                                    // (OOM, StackOverflow) that would otherwise kill the app.
                                    binding.imgPreview.isVisible = false
                                }
                            }

                            override fun onSuccess() {
                                binding.extIndicator.isVisible = false
                            }
                        })

                } catch (e: Throwable) {
                    Log.e("SPRViewer", "ERR", e)
                    binding.imgPreview.isVisible = false
                }

                binding.drawableName.text = "${info.name}.${info.ext}"
                binding.extIndicator.setText(info.ext)
                binding.drawablePath.text = info.path

                setOnClickListener {
                    itemSelectedListener(getInfo(bindingAdapterPosition))
                }
            }
        }
    }
}