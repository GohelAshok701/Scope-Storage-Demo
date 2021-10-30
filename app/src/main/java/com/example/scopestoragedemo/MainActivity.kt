package com.example.scopestoragedemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.*

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
                intent.action = Intent.ACTION_GET_CONTENT
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

                val docUri = DocumentsContract.buildDocumentUriUsingTree(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, "")),
                    DocumentsContract.getTreeDocumentId(Uri.parse(SpUtil.getString(SpUtil.FOLDER_URI, ""))));
                copyFileUsingStream(File(realPath),
                    File(RealPathUtil.getRealPath(this, docUri) + File.separator + RealPathUtil.getFileName(this,data.data)))
            }
        }
    }

    @Throws(IOException::class)
    private fun copyFileUsingStream(source: File, dest: File) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = FileInputStream(source)
            outputStream = FileOutputStream(dest)
            val buffer = ByteArray(1024)
            var length: Int
            while (inputStream.read(buffer).also { length = it } > 0) {
                outputStream.write(buffer, 0, length)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (inputStream != null) {
                inputStream.close()
            }
            if (outputStream != null) {
                outputStream.close()
            }
        }
    }
}