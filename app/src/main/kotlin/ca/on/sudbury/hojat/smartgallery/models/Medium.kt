package ca.on.sudbury.hojat.smartgallery.models

import android.content.Context
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ColumnInfo
import androidx.room.Ignore
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_GIFS
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_IMAGES
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_VIDEOS
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_RAWS
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_SVGS
import ca.on.sudbury.hojat.smartgallery.helpers.TYPE_PORTRAITS
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_LAST_MODIFIED_DAILY
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_LAST_MODIFIED_MONTHLY
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_DATE_TAKEN_DAILY
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_DATE_TAKEN_MONTHLY
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_FILE_TYPE
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_EXTENSION
import ca.on.sudbury.hojat.smartgallery.helpers.GROUP_BY_FOLDER
import com.bumptech.glide.signature.ObjectKey
import ca.on.sudbury.hojat.smartgallery.extensions.isApng
import ca.on.sudbury.hojat.smartgallery.extensions.formatDate
import ca.on.sudbury.hojat.smartgallery.helpers.SORT_BY_NAME
import ca.on.sudbury.hojat.smartgallery.helpers.SORT_BY_PATH
import ca.on.sudbury.hojat.smartgallery.helpers.SORT_BY_SIZE
import ca.on.sudbury.hojat.smartgallery.helpers.SORT_BY_DATE_MODIFIED
import ca.on.sudbury.hojat.smartgallery.helpers.SORT_BY_RANDOM
import ca.on.sudbury.hojat.smartgallery.usecases.FormatFileSizeUseCase
import ca.on.sudbury.hojat.smartgallery.usecases.GetFileExtensionUseCase
import ca.on.sudbury.hojat.smartgallery.usecases.IsWebpUseCase
import java.io.File
import java.io.Serializable
import java.util.Calendar
import java.util.Locale

@Entity(tableName = "media", indices = [(Index(value = ["full_path"], unique = true))])
data class Medium(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "filename") var name: String,
    @ColumnInfo(name = "full_path") var path: String,
    @ColumnInfo(name = "parent_path") var parentPath: String,
    @ColumnInfo(name = "last_modified") var modified: Long,
    @ColumnInfo(name = "date_taken") var taken: Long,
    @ColumnInfo(name = "size") var size: Long,
    @ColumnInfo(name = "type") var type: Int,
    @ColumnInfo(name = "video_duration") var videoDuration: Int,
    @ColumnInfo(name = "is_favorite") var isFavorite: Boolean,
    @ColumnInfo(name = "deleted_ts") var deletedTS: Long,
    @ColumnInfo(name = "media_store_id") var mediaStoreId: Long,

    @Ignore var gridPosition: Int = 0   // used at grid view decoration at Grouping enabled
) : Serializable, ThumbnailItem() {

    constructor() : this(null, "", "", "", 0L, 0L, 0L, 0, 0, false, 0L, 0L, 0)

    companion object {
        private const val serialVersionUID = -6553149366975655L
    }

    fun isWebP() = IsWebpUseCase(name)

    fun isGIF() = type == TYPE_GIFS

    fun isImage() = type == TYPE_IMAGES

    fun isVideo() = type == TYPE_VIDEOS

    fun isRaw() = type == TYPE_RAWS

    fun isSVG() = type == TYPE_SVGS

    fun isPortrait() = type == TYPE_PORTRAITS

    fun isApng() = name.isApng()

    fun isHidden() = name.startsWith('.')

    fun isHeic() = name.lowercase(Locale.ROOT).endsWith(".heic") || name.lowercase(Locale.ROOT)
        .endsWith(".heif")

    fun getBubbleText(sorting: Int, context: Context, dateFormat: String, timeFormat: String) =
        when {
            sorting and SORT_BY_NAME != 0 -> name
            sorting and SORT_BY_PATH != 0 -> path
            sorting and SORT_BY_SIZE != 0 -> FormatFileSizeUseCase(size)
            sorting and SORT_BY_DATE_MODIFIED != 0 -> modified.formatDate(
                context,
                dateFormat,
                timeFormat
            )
            sorting and SORT_BY_RANDOM != 0 -> name
            else -> taken.formatDate(context)
        }

    fun getGroupingKey(groupBy: Int): String {
        return when {
            groupBy and GROUP_BY_LAST_MODIFIED_DAILY != 0 -> getDayStartTS(modified, false)
            groupBy and GROUP_BY_LAST_MODIFIED_MONTHLY != 0 -> getDayStartTS(modified, true)
            groupBy and GROUP_BY_DATE_TAKEN_DAILY != 0 -> getDayStartTS(taken, false)
            groupBy and GROUP_BY_DATE_TAKEN_MONTHLY != 0 -> getDayStartTS(taken, true)
            groupBy and GROUP_BY_FILE_TYPE != 0 -> type.toString()
            groupBy and GROUP_BY_EXTENSION != 0 -> GetFileExtensionUseCase(name).lowercase(Locale.ROOT)
            groupBy and GROUP_BY_FOLDER != 0 -> parentPath
            else -> ""
        }
    }

    fun getIsInRecycleBin() = deletedTS != 0L

    private fun getDayStartTS(ts: Long, resetDays: Boolean): String {
        val calendar = Calendar.getInstance(Locale.ENGLISH).apply {
            timeInMillis = ts
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            if (resetDays) {
                set(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return calendar.timeInMillis.toString()
    }

    fun getSignature(): String {
        val lastModified = if (modified > 1) {
            modified
        } else {
            File(path).lastModified()
        }

        return "$path-$lastModified-$size"
    }

    fun getKey() = ObjectKey(getSignature())

    fun toFileDirItem() = FileDirItem(path, name, false, 0, size, modified, mediaStoreId)
}
