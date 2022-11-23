package ca.on.sudbury.hojat.smartgallery.dialogs

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import ca.on.sudbury.hojat.smartgallery.R
import ca.on.sudbury.hojat.smartgallery.activities.BaseSimpleActivity
import ca.on.sudbury.hojat.smartgallery.extensions.getProperPrimaryColor
import ca.on.sudbury.hojat.smartgallery.extensions.getAlertDialogBuilder
import ca.on.sudbury.hojat.smartgallery.extensions.setupDialogStuff
import ca.on.sudbury.hojat.smartgallery.extensions.handleHiddenFolderPasswordProtection
import ca.on.sudbury.hojat.smartgallery.extensions.handleLockedFolderOpening
import ca.on.sudbury.hojat.smartgallery.extensions.isRestrictedWithSAFSdk30
import ca.on.sudbury.hojat.smartgallery.extensions.isInDownloadDir
import ca.on.hojat.palette.views.MyGridLayoutManager
import ca.on.sudbury.hojat.smartgallery.adapters.DirectoryAdapter
import ca.on.sudbury.hojat.smartgallery.databinding.DialogDirectoryPickerBinding
import ca.on.sudbury.hojat.smartgallery.extensions.config
import ca.on.sudbury.hojat.smartgallery.extensions.getCachedDirectories
import ca.on.sudbury.hojat.smartgallery.extensions.addTempFolderIfNeeded
import ca.on.sudbury.hojat.smartgallery.extensions.getDistinctPath
import ca.on.sudbury.hojat.smartgallery.extensions.getSortedDirectories
import ca.on.sudbury.hojat.smartgallery.extensions.getDirsToShow
import ca.on.sudbury.hojat.smartgallery.helpers.ViewType
import ca.on.sudbury.hojat.smartgallery.models.Directory
import ca.on.sudbury.hojat.smartgallery.usecases.BeVisibleOrGoneUseCase
import timber.log.Timber

/**
 * This dialog is meant to allow the user to choose a folder; and is being called from
 * various places in the app:
 *
 * 1- While you're adding the app widget of this app to your launcher, in the widget
 * configuration page click on the button below "Folder shown on the widget:" and the
 * resulting dialog is created via this class.
 *
 * 2- In any folders, long click on one or more of pics/vids and from context menu click
 * on "copy to" or "move to". The resulting dialog is created via this class.
 *
 * 3- .....
 *
 */
@SuppressLint("InflateParams")
class PickDirectoryDialog(
    val activity: BaseSimpleActivity,
    val sourcePath: String,
    showOtherFolderButton: Boolean,
    val showFavoritesBin: Boolean,
    val isPickingCopyMoveDestination: Boolean,
    val isPickingFolderForWidget: Boolean,
    val callback: (path: String) -> Unit
) {

    // we create the binding by referencing the owner Activity
    var binding = DialogDirectoryPickerBinding.inflate(activity.layoutInflater)

    private var dialog: AlertDialog? = null
    private var shownDirectories = ArrayList<Directory>()
    private var allDirectories = ArrayList<Directory>()
    private var openedSubfolders = arrayListOf("")

    private var isGridViewType = activity.config.viewTypeFolders == ViewType.Grid.id
    private var showHidden = activity.config.shouldShowHidden
    private var currentPathPrefix = ""

    init {
        Timber.d("Hojat Ghasemi : PickDirectoryDialog was called")
        (binding.directoriesGrid.layoutManager as MyGridLayoutManager).apply {
            orientation =
                if (activity.config.scrollHorizontally && isGridViewType) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
            spanCount = if (isGridViewType) activity.config.dirColumnCnt else 1
        }

        binding.directoriesFastscroller.updateColors(activity.getProperPrimaryColor())

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .setOnKeyListener { _, i, keyEvent ->
                if (keyEvent.action == KeyEvent.ACTION_UP && i == KeyEvent.KEYCODE_BACK) {
                    backPressed()
                }
                true
            }

        if (showOtherFolderButton) {
            builder.setNeutralButton(R.string.other_folder) { _, _ -> showOtherFolder() }
        }

        builder.apply {
            activity.setupDialogStuff(
                binding.root,
                this,
                R.string.select_destination
            ) { alertDialog ->
                dialog = alertDialog
                BeVisibleOrGoneUseCase(
                    binding.directoriesShowHidden,
                    !context.config.shouldShowHidden
                )
                binding.directoriesShowHidden.setOnClickListener {
                    activity.handleHiddenFolderPasswordProtection {
                        binding.directoriesShowHidden.visibility = View.GONE
                        showHidden = true
                        fetchDirectories(true)
                    }
                }
            }
        }

        fetchDirectories(false)
    }

    private fun fetchDirectories(forceShowHidden: Boolean) {
        activity.getCachedDirectories(forceShowHidden = forceShowHidden) {
            if (it.isNotEmpty()) {
                it.forEach { directory ->
                    directory.subfoldersMediaCount = directory.mediaCnt
                }

                activity.runOnUiThread {
                    gotDirectories(activity.addTempFolderIfNeeded(it))
                }
            }
        }
    }

    private fun showOtherFolder() {
        FilePickerDialog(
            activity,
            sourcePath,
            !isPickingCopyMoveDestination && !isPickingFolderForWidget,
            showHidden,
            showFAB = true,
            canAddShowHiddenButton = true
        ) {
            activity.handleLockedFolderOpening(it) { success ->
                if (success) {
                    callback(it)
                }
            }
        }
    }

    private fun gotDirectories(newDirs: ArrayList<Directory>) {
        if (allDirectories.isEmpty()) {
            allDirectories = newDirs.clone() as ArrayList<Directory>
        }

        val distinctDirs =
            newDirs.filter { showFavoritesBin || (!it.isRecycleBin() && !it.areFavorites()) }
                .distinctBy { it.path.getDistinctPath() }
                .toMutableList() as ArrayList<Directory>
        val sortedDirs = activity.getSortedDirectories(distinctDirs)
        val dirs = activity.getDirsToShow(sortedDirs, allDirectories, currentPathPrefix)
            .clone() as ArrayList<Directory>
        if (dirs.hashCode() == shownDirectories.hashCode()) {
            return
        }

        shownDirectories = dirs
        val adapter = DirectoryAdapter(
            activity,
            dirs.clone() as ArrayList<Directory>,
            null,
            binding.directoriesGrid,
            true
        ) {
            val clickedDir = it as Directory
            val path = clickedDir.path
            if (clickedDir.subfoldersCount == 1 || !activity.config.groupDirectSubfolders) {
                if (isPickingCopyMoveDestination && path.trimEnd('/') == sourcePath) {
                    Toast.makeText(
                        activity,
                        R.string.source_and_destination_same,
                        Toast.LENGTH_LONG
                    ).show()
                    return@DirectoryAdapter
                } else if (isPickingCopyMoveDestination && activity.isRestrictedWithSAFSdk30(path) && !activity.isInDownloadDir(
                        path
                    )
                ) {
                    Toast.makeText(
                        activity,
                        R.string.system_folder_copy_restriction,
                        Toast.LENGTH_LONG
                    ).show()
                    return@DirectoryAdapter
                } else {
                    activity.handleLockedFolderOpening(path) { success ->
                        if (success) {
                            callback(path)
                        }
                    }
                    dialog?.dismiss()
                }
            } else {
                currentPathPrefix = path
                openedSubfolders.add(path)
                gotDirectories(allDirectories)
            }
        }

        val scrollHorizontally = activity.config.scrollHorizontally && isGridViewType
        binding.apply {
            directoriesGrid.adapter = adapter
            directoriesFastscroller.setScrollVertically(!scrollHorizontally)
        }
    }

    private fun backPressed() {
        if (activity.config.groupDirectSubfolders) {
            if (currentPathPrefix.isEmpty()) {
                dialog?.dismiss()
            } else {
                openedSubfolders.removeAt(openedSubfolders.size - 1)
                currentPathPrefix = openedSubfolders.last()
                gotDirectories(allDirectories)
            }
        } else {
            dialog?.dismiss()
        }
    }
}
