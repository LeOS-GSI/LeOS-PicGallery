package ca.on.sudbury.hojat.smartgallery.helpers

import android.graphics.Bitmap
import ca.on.sudbury.hojat.smartgallery.models.FilterItem

class FilterThumbnailsManager {
    private var filterThumbnails = ArrayList<FilterItem>(10)
    private var processedThumbnails = ArrayList<FilterItem>(10)

    fun addThumb(filterItem: FilterItem) {
        filterThumbnails.add(filterItem)
    }

    fun processThumbs(): ArrayList<FilterItem> {
        for (filterItem in filterThumbnails) {
            filterItem.bitmap =
                filterItem.filter.processFilter(Bitmap.createBitmap(filterItem.bitmap))
            processedThumbnails.add(filterItem)
        }
        return processedThumbnails
    }

    fun clearThumbs() {
        filterThumbnails = ArrayList()
        processedThumbnails = ArrayList()
    }
}
