package ca.on.sudbury.hojat.smartgallery.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.RelativeLayout
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.on.sudbury.hojat.smartgallery.R
import ca.on.sudbury.hojat.smartgallery.extensions.getProperTextColor
import ca.on.sudbury.hojat.smartgallery.extensions.getProperPrimaryColor
import ca.on.sudbury.hojat.smartgallery.extensions.beVisible
import ca.on.sudbury.hojat.smartgallery.extensions.beGone
import ca.on.sudbury.hojat.smartgallery.extensions.recycleBinPath
import ca.on.sudbury.hojat.smartgallery.extensions.isVideoFast
import ca.on.sudbury.hojat.smartgallery.extensions.isMediaFile
import ca.on.sudbury.hojat.smartgallery.extensions.toast
import ca.on.sudbury.hojat.smartgallery.extensions.deleteFiles
import ca.on.sudbury.hojat.smartgallery.helpers.NavigationIcon
import ca.on.sudbury.hojat.smartgallery.helpers.VIEW_TYPE_GRID
import ca.on.sudbury.hojat.smartgallery.models.FileDirItem
import ca.on.hojat.palette.views.MyGridLayoutManager
import ca.on.sudbury.hojat.smartgallery.adapters.MediaAdapter
import ca.on.sudbury.hojat.smartgallery.asynctasks.GetMediaAsynctask
import ca.on.sudbury.hojat.smartgallery.base.SimpleActivity
import ca.on.sudbury.hojat.smartgallery.helpers.GridSpacingItemDecoration
import ca.on.sudbury.hojat.smartgallery.helpers.MediaFetcher
import ca.on.sudbury.hojat.smartgallery.helpers.PATH
import ca.on.sudbury.hojat.smartgallery.helpers.SHOW_ALL
import ca.on.sudbury.hojat.smartgallery.database.MediaOperationsListener
import ca.on.sudbury.hojat.smartgallery.databinding.ActivitySearchBinding
import ca.on.sudbury.hojat.smartgallery.extensions.config
import ca.on.sudbury.hojat.smartgallery.extensions.deleteDBPath
import ca.on.sudbury.hojat.smartgallery.extensions.openPath
import ca.on.sudbury.hojat.smartgallery.extensions.getCachedMedia
import ca.on.sudbury.hojat.smartgallery.extensions.movePathsInRecycleBin
import ca.on.sudbury.hojat.smartgallery.models.Medium
import ca.on.sudbury.hojat.smartgallery.models.ThumbnailItem
import ca.on.sudbury.hojat.smartgallery.usecases.RunOnBackgroundThreadUseCase
import java.io.File

class SearchActivity : SimpleActivity(), MediaOperationsListener {

    private lateinit var binding: ActivitySearchBinding
    private var mIsSearchOpen = false
    private var mLastSearchedText = ""

    private var mSearchMenuItem: MenuItem? = null
    private var mCurrAsyncTask: GetMediaAsynctask? = null
    private var mAllMedia = ArrayList<ThumbnailItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupOptionsMenu()
        binding.searchEmptyTextPlaceholder.setTextColor(getProperTextColor())
        getAllMedia()
        binding.searchFastscroller.updateColors(getProperPrimaryColor())
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.searchToolbar, NavigationIcon.Arrow, searchMenuItem = mSearchMenuItem)
    }

    override fun onDestroy() {
        super.onDestroy()
        mCurrAsyncTask?.stopFetching()
    }

    private fun setupOptionsMenu() {
        setupSearch(binding.searchToolbar.menu)
        binding.searchToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.toggle_filename -> toggleFilenameVisibility()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        mSearchMenuItem = menu.findItem(R.id.search)
        MenuItemCompat.setOnActionExpandListener(
            mSearchMenuItem,
            object : MenuItemCompat.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                    mIsSearchOpen = true
                    return true
                }

                // this triggers on device rotation too, avoid doing anything
                override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                    if (mIsSearchOpen) {
                        mIsSearchOpen = false
                        mLastSearchedText = ""
                    }
                    return true
                }
            })
        mSearchMenuItem?.expandActionView()

        (mSearchMenuItem?.actionView as? SearchView)?.apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (mIsSearchOpen) {
                        mLastSearchedText = newText
                        textChanged(newText)
                    }
                    return true
                }
            })
        }
    }

    private fun textChanged(text: String) {
        RunOnBackgroundThreadUseCase {
            try {
                val filtered =
                    mAllMedia.filter { it is Medium && it.name.contains(text, true) } as ArrayList
                filtered.sortBy { it is Medium && !it.name.startsWith(text, true) }
                val grouped =
                    MediaFetcher(applicationContext).groupMedia(filtered as ArrayList<Medium>, "")
                runOnUiThread {
                    if (grouped.isEmpty()) {
                        binding.searchEmptyTextPlaceholder.text = getString(R.string.no_items_found)
                        binding.searchEmptyTextPlaceholder.beVisible()
                    } else {
                        binding.searchEmptyTextPlaceholder.beGone()
                    }

                    handleGridSpacing(grouped)
                    getMediaAdapter()?.updateMedia(grouped)
                }
            } catch (ignored: Exception) {
            }
        }
    }

    private fun setupAdapter() {
        val currAdapter = binding.searchGrid.adapter
        if (currAdapter == null) {
            MediaAdapter(
                activity = this,
                ArrayList(),
                listener = this,
                isAGetIntent = false,
                allowMultiplePicks = false,
                path = "",
                binding.searchGrid
            ) {
                if (it is Medium) {
                    itemClicked(it.path)
                }
            }.apply {
                binding.searchGrid.adapter = this
            }
            setupLayoutManager()
            handleGridSpacing(mAllMedia)
        } else if (mLastSearchedText.isEmpty()) {
            (currAdapter as MediaAdapter).updateMedia(mAllMedia)
            handleGridSpacing(mAllMedia)
        } else {
            textChanged(mLastSearchedText)
        }

        setupScrollDirection()
    }

    private fun handleGridSpacing(media: ArrayList<ThumbnailItem>) {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            if (binding.searchGrid.itemDecorationCount > 0) {
                binding.searchGrid.removeItemDecorationAt(0)
            }

            val spanCount = config.mediaColumnCnt
            val spacing = config.thumbnailSpacing
            val decoration = GridSpacingItemDecoration(
                spanCount,
                spacing,
                config.scrollHorizontally,
                config.fileRoundedCorners,
                media,
                true
            )
            binding.searchGrid.addItemDecoration(decoration)
        }
    }

    private fun getMediaAdapter() = binding.searchGrid.adapter as? MediaAdapter

    private fun toggleFilenameVisibility() {
        config.displayFileNames = !config.displayFileNames
        getMediaAdapter()?.updateDisplayFilenames(config.displayFileNames)
    }

    private fun itemClicked(path: String) {
        val isVideo = path.isVideoFast()
        if (isVideo) {
            openPath(path, false)
        } else {
            Intent(this, ViewPagerActivity::class.java).apply {
                putExtra(PATH, path)
                putExtra(SHOW_ALL, false)
                startActivity(this)
            }
        }
    }

    private fun setupLayoutManager() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        if (viewType == VIEW_TYPE_GRID) {
            setupGridLayoutManager()
        } else {
            setupListLayoutManager()
        }
    }

    private fun setupGridLayoutManager() {
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        if (config.scrollHorizontally) {
            layoutManager.orientation = RecyclerView.HORIZONTAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        } else {
            layoutManager.orientation = RecyclerView.VERTICAL
            binding.searchGrid.layoutParams = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        layoutManager.spanCount = config.mediaColumnCnt
        val adapter = getMediaAdapter()
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter?.isASectionTitle(position) == true) {
                    layoutManager.spanCount
                } else {
                    1
                }
            }
        }
    }

    private fun setupListLayoutManager() {
        val layoutManager = binding.searchGrid.layoutManager as MyGridLayoutManager
        layoutManager.spanCount = 1
        layoutManager.orientation = RecyclerView.VERTICAL
    }

    private fun setupScrollDirection() {
        val viewType = config.getFolderViewType(SHOW_ALL)
        val scrollHorizontally = config.scrollHorizontally && viewType == VIEW_TYPE_GRID
        binding.searchFastscroller.setScrollVertically(!scrollHorizontally)
    }

    private fun getAllMedia() {
        getCachedMedia("") {
            if (it.isNotEmpty()) {
                mAllMedia = it.clone() as ArrayList<ThumbnailItem>
            }
            runOnUiThread {
                setupAdapter()
            }
            startAsyncTask(false)
        }
    }

    private fun startAsyncTask(updateItems: Boolean) {
        mCurrAsyncTask?.stopFetching()
        mCurrAsyncTask = GetMediaAsynctask(applicationContext, "", showAll = true) {
            mAllMedia = it.clone() as ArrayList<ThumbnailItem>
            if (updateItems) {
                textChanged(mLastSearchedText)
            }
        }

        mCurrAsyncTask!!.execute()
    }

    override fun refreshItems() {
        startAsyncTask(true)
    }

    override fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>) {
        val filtered =
            fileDirItems.filter { File(it.path).isFile && it.path.isMediaFile() } as ArrayList
        if (filtered.isEmpty()) {
            return
        }

        if (config.useRecycleBin && !filtered.first().path.startsWith(recycleBinPath)) {
            val movingItems = resources.getQuantityString(
                R.plurals.moving_items_into_bin,
                filtered.size,
                filtered.size
            )
            toast(movingItems)

            movePathsInRecycleBin(filtered.map { it.path } as ArrayList<String>) {
                if (it) {
                    deleteFilteredFiles(filtered)
                } else {
                    toast(R.string.unknown_error_occurred)
                }
            }
        } else {
            val deletingItems =
                resources.getQuantityString(R.plurals.deleting_items, filtered.size, filtered.size)
            toast(deletingItems)
            deleteFilteredFiles(filtered)
        }
    }

    private fun deleteFilteredFiles(filtered: ArrayList<FileDirItem>) {
        deleteFiles(filtered) {
            if (!it) {
                toast(R.string.unknown_error_occurred)
                return@deleteFiles
            }

            mAllMedia.removeAll { filtered.map { it.path }.contains((it as? Medium)?.path) }

            RunOnBackgroundThreadUseCase {
                val useRecycleBin = config.useRecycleBin
                filtered.forEach {
                    if (it.path.startsWith(recycleBinPath) || !useRecycleBin) {
                        deleteDBPath(it.path)
                    }
                }
            }
        }
    }

    override fun selectedPaths(paths: ArrayList<String>) {}

    override fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>) {}
}
