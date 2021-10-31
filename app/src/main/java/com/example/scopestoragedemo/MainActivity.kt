package com.example.scopestoragedemo

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {

    lateinit var btnPickFile: Button
    val LOGTAG = "MainActivity"
    val FOLDER_ACCESS_REQUEST = 101
    val FILE_PICK_REQUEST = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btnPickFile = findViewById(R.id.btnPickFile)
        btnPickFile.setOnClickListener {
            openDocumentTree()
        }
    }

    private fun openDocumentTree() {
        val uriString = SpUtil.getString(SpUtil.FOLDER_URI, "")
        when {
            uriString == "" -> {
                Log.w(LOGTAG, "uri not stored")
                askPermission()
            }
            arePermissionsGranted(uriString) -> {
                val intent = Intent()
                intent.action = Intent.ACTION_OPEN_DOCUMENT
                intent.type = "application/pdf"
                startActivityForResult(intent, FILE_PICK_REQUEST)
            }
            else -> {
                askPermission()
            }
        }
    }

    private fun askPermission() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, FOLDER_ACCESS_REQUEST)
    }

    private fun releasePermissions(uri: Uri) {
        val flags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.releasePersistableUriPermission(uri, flags)
        SpUtil.storeString(SpUtil.FOLDER_URI, "")
    }

    private fun arePermissionsGranted(uriString: String): Boolean {
        val list = contentResolver.persistedUriPermissions
        for (i in list.indices) {
            val persistedUriString = list[i].uri.toString()
            if (persistedUriString == uriString && list[i].isWritePermission && list[i].isReadPermission) {
                return true
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && requestCode == FOLDER_ACCESS_REQUEST) {
            if (data != null) {
                val treeUri: Uri? = data.data
                if (treeUri != null) {
                    Log.i(LOGTAG, "got uri: ${treeUri.toString()}")
                    if (Uri.decode(treeUri.toString()).endsWith(":")) {
                        Toast.makeText(this, "Cannot use root folder!", Toast.LENGTH_SHORT).show()
                        // consider asking user to select another folder
                        return
                    }
                    val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    contentResolver.takePersistableUriPermission(
                        treeUri,
                        takeFlags
                    )
                    SpUtil.storeString(SpUtil.FOLDER_URI, treeUri.toString())
                }
            }
        } else if (resultCode == RESULT_OK && requestCode == FILE_PICK_REQUEST) {
            if (data != null) {
                var realPath = RealPathUtil.getRealPath(this, data.data)
                Log.d(LOGTAG, "onActivityResult: " + realPath)
                /*copyFileUsingStream(File(realPath),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS + File.separator + RealPathUtil.getFileName(this,data.data)))*/

                /*val docUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, "")),
                    DocumentsContract.getTreeDocumentId(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, ""))));
                copyFileUsingStream(File(realPath),
                    File(RealPathUtil.getRealPath(this, docUri) + File.separator + RealPathUtil.getFileName(this,data.data)))*/

                data.data?.let { copyUriToExternalFilesDir(it, File(realPath).name) }
                /*saveFileToExternalStorage(RealPathUtil.getFileName(this, data.data),"ABC TESTING")*/
            }
        }
    }

    @Throws(IOException::class)
    private fun copyFileUsingStream(source: File, dest: File) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            val newdestFile = dest.createNewFile()
            if (newdestFile){
                /*inputStream = FileInputStream(source)
                outputStream = FileOutputStream(dest)
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }*/
                val fos = FileOutputStream(dest)
                val bis = BufferedInputStream(inputStream)
                val bos = BufferedOutputStream(fos)
                val byteArray = ByteArray(1024)
                var bytes = bis.read(byteArray)
                while (bytes > 0) {
                    bos.write(byteArray, 0, bytes)
                    bos.flush()
                    bytes = bis.read(byteArray)
                }
                bos.close()
                fos.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
        } finally {
            if (inputStream != null) {
                inputStream.close()
            }
            if (outputStream != null) {
                outputStream.close()
            }
        }
    }

    private fun copyUriToExternalFilesDir(uri: Uri, fileName: String) {
        thread {
            val inputStream = contentResolver.openInputStream(uri)
            val tempDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (inputStream != null && tempDir != null) {
                val file = File("$tempDir/$fileName")
                val fos = FileOutputStream(file)
                val bis = BufferedInputStream(inputStream)
                val bos = BufferedOutputStream(fos)
                val byteArray = ByteArray(1024)
                var bytes = bis.read(byteArray)
                while (bytes > 0) {
                    bos.write(byteArray, 0, bytes)
                    bos.flush()
                    bytes = bis.read(byteArray)
                }
                bos.close()
                fos.close()
                runOnUiThread {
                    Toast.makeText(this, "Copy file into $tempDir succeeded.", Toast.LENGTH_LONG)
                        .show()
                }
            }
        }

        Handler().postDelayed({
            val destFolderUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, "")),
                DocumentsContract.getTreeDocumentId(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, ""))))
            copyFileIntoFilesDir(this, destFolderUri, uri)
        }, 5000)

        /*val handler = Handler()
        handler.postDelayed({
            val tempFile =
                getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS + File.separator + fileName)
            if (tempFile!!.exists()) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(
                    Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, "")),
                    DocumentsContract.getTreeDocumentId(
                        Uri.parse(
                            SpUtil.getString(
                                SpUtil.FOLDER_URI,
                                ""
                            )
                        )
                    )
                );

                copyFileUsingStream(
                    tempFile,
                    File(RealPathUtil.getRealPath(this, docUri) + File.separator + tempFile.name)
                )
            }
        }, 5000)*/
    }

    private fun saveFileToExternalStorage(displayName: String, content: String) {
        try {
            val externalUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val relativeLocation = Environment.DIRECTORY_DOCUMENTS
            val contentValues = ContentValues()
            contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$displayName")
            contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/pdf")
            contentValues.put(MediaStore.Files.FileColumns.TITLE, "Test")
            contentValues.put(
                MediaStore.Files.FileColumns.DATE_ADDED,
                System.currentTimeMillis() / 1000
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativeLocation)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(
                    MediaStore.Files.FileColumns.DATE_TAKEN,
                    System.currentTimeMillis()
                )
            }
            val fileUri = contentResolver.insert(externalUri, contentValues)

            val outputStream = contentResolver.openOutputStream(fileUri!!)
            outputStream!!.write(content.toByteArray())
            outputStream.close()
        } catch (e: IOException) {
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG)
                .show()
        }
    }

    fun copyFileIntoFilesDir(context: Context, destFolderUri: Uri, selectedFileUri: Uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            // r for read
            try {
                val parcelFileDescriptor =
                    context.contentResolver.openFileDescriptor(selectedFileUri, "r", null)
                val inputStream = FileInputStream(parcelFileDescriptor?.fileDescriptor)
                val destinationFile = File(
                    context.filesDir,
                    context.contentResolver.getFileName(selectedFileUri)
                )
                val outputStream = FileOutputStream(destinationFile)
                copy(inputStream, outputStream)
            } catch (e: Exception) {
                Log.e("ScopeStoreUtils", "copyFileIntoFilesDir: Exception: $e")
            }
        }
    }

    private fun ContentResolver.getFileName(fileUri: Uri): String {
        var name = ""
        val returnCursor = this.query(fileUri, null, null, null, null)
        if (returnCursor != null) {
            val nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            returnCursor.moveToFirst()
            name = returnCursor.getString(nameIndex)
            returnCursor.close()
        }
        return name
    }


    @Throws(IOException::class)
    fun copy(input: InputStream?, output: OutputStream?): Int {
        val count = copyLarge(input!!, output!!)
        return if (count > Int.MAX_VALUE) {
            -1
        } else count.toInt()
    }

    @Throws(IOException::class)
    fun copyLarge(input: InputStream, output: OutputStream): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count: Long = 0
        var n: Int
        while (-1 != input.read(buffer).also { n = it }) {
            output.write(buffer, 0, n)
            count += n.toLong()
        }
        return count
    }
}