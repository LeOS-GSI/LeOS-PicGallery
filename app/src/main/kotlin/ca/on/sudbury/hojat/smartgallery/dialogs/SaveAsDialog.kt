package ca.on.sudbury.hojat.smartgallery.dialogs

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import ca.on.sudbury.hojat.smartgallery.R
import ca.on.sudbury.hojat.smartgallery.databinding.DialogSaveAsBinding
import ca.on.sudbury.hojat.smartgallery.extensions.humanizePath
import ca.on.sudbury.hojat.smartgallery.activities.BaseSimpleActivity
import ca.on.sudbury.hojat.smartgallery.extensions.getParentPath
import ca.on.sudbury.hojat.smartgallery.extensions.getPicturesDirectoryPath
import ca.on.sudbury.hojat.smartgallery.extensions.isRestrictedWithSAFSdk30
import ca.on.sudbury.hojat.smartgallery.extensions.isInDownloadDir
import ca.on.sudbury.hojat.smartgallery.extensions.getFilenameFromPath
import ca.on.sudbury.hojat.smartgallery.extensions.getAlertDialogBuilder
import ca.on.sudbury.hojat.smartgallery.extensions.setupDialogStuff
import ca.on.sudbury.hojat.smartgallery.extensions.isAValidFilename
import ca.on.sudbury.hojat.smartgallery.extensions.getDoesFilePathExist
import ca.on.sudbury.hojat.smartgallery.extensions.isInSubFolderInDownloadDir
import ca.on.sudbury.hojat.smartgallery.extensions.toFileDirItem
import ca.on.sudbury.hojat.smartgallery.extensions.isExternalStorageManager
import ca.on.sudbury.hojat.smartgallery.extensions.getFileUrisFromFileDirItems
import ca.on.sudbury.hojat.smartgallery.usecases.IsRPlusUseCase
import ca.on.sudbury.hojat.smartgallery.usecases.HideKeyboardUseCase
import ca.on.sudbury.hojat.smartgallery.usecases.ShowKeyboardUseCase
import java.io.File

// TODO: this dialog should be replaced by DialogFragment.

@SuppressLint("InflateParams", "SetTextI18n")
class SaveAsDialog(
    val activity: BaseSimpleActivity,
    val path: String,
    private val appendFilename: Boolean,
    private val cancelCallback: (() -> Unit)? = null,
    val callback: (savePath: String) -> Unit
) {
    init {

        // we create the binding by referencing the owner Activity
        val binding = DialogSaveAsBinding.inflate(activity.layoutInflater)

        var realPath = path.getParentPath()
        if (activity.isRestrictedWithSAFSdk30(realPath) && !activity.isInDownloadDir(realPath)) {
            realPath = activity.getPicturesDirectoryPath(realPath)
        }

        binding.apply {
            folderValue.setText("${activity.humanizePath(realPath).trimEnd('/')}/")

            val fullName = path.getFilenameFromPath()
            val dotAt = fullName.lastIndexOf(".")
            var name = fullName

            if (dotAt > 0) {
                name = fullName.substring(0, dotAt)
                val extension = fullName.substring(dotAt + 1)
                binding.extensionValue.setText(extension)
            }

            if (appendFilename) {
                name += "_1"
            }

            binding.filenameValue.setText(name)
            binding.folderValue.setOnClickListener {
                HideKeyboardUseCase(activity, binding.folderValue)
                FilePickerDialog(
                    activity,
                    realPath,
                    pickFile = false,
                    showHidden = false,
                    showFAB = true,
                    canAddShowHiddenButton = true
                ) {
                    binding.folderValue.setText(activity.humanizePath(it))
                    realPath = it
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel) { _, _ -> cancelCallback?.invoke() }
            .setOnCancelListener { cancelCallback?.invoke() }
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.save_as) { alertDialog ->
                    ShowKeyboardUseCase(alertDialog, binding.filenameValue)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.filenameValue.text.toString().trim()
                        val extension = binding.extensionValue.text.toString().trim()

                        if (filename.isEmpty()) {
                            Toast.makeText(
                                activity,
                                R.string.filename_cannot_be_empty,
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }

                        if (extension.isEmpty()) {
                            Toast.makeText(
                                activity,
                                R.string.extension_cannot_be_empty,
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }

                        val newFilename = "$filename.$extension"
                        val newPath = "${realPath.trimEnd('/')}/$newFilename"
                        if (!newFilename.isAValidFilename()) {
                            Toast.makeText(
                                activity,
                                R.string.filename_invalid_characters,
                                Toast.LENGTH_LONG
                            ).show()
                            return@setOnClickListener
                        }

                        if (activity.getDoesFilePathExist(newPath)) {
                            val title = String.format(
                                activity.getString(R.string.file_already_exists_overwrite),
                                newFilename
                            )
                            ConfirmationDialog(activity, title) {
                                val newFile = File(newPath)
                                val isInDownloadDir = activity.isInDownloadDir(newPath)
                                val isInSubFolderInDownloadDir =
                                    activity.isInSubFolderInDownloadDir(newPath)
                                if ((IsRPlusUseCase() && !isExternalStorageManager()) && isInDownloadDir && !isInSubFolderInDownloadDir && !newFile.canWrite()) {
                                    val fileDirItem =
                                        arrayListOf(File(newPath).toFileDirItem(activity))
                                    val fileUris = activity.getFileUrisFromFileDirItems(fileDirItem)
                                    activity.updateSDK30Uris(fileUris) { success ->
                                        if (success) {
                                            selectPath(alertDialog, newPath)
                                        }
                                    }
                                } else {
                                    selectPath(alertDialog, newPath)
                                }
                            }
                        } else {
                            selectPath(alertDialog, newPath)
                        }
                    }
                }
            }
    }

    private fun selectPath(alertDialog: AlertDialog, newPath: String) {
        activity.handleSAFDialogSdk30(newPath) {
            if (!it) {
                return@handleSAFDialogSdk30
            }
            callback(newPath)
            alertDialog.dismiss()
        }
    }
}
