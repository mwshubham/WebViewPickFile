package com.example.webviewpickfile

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

/**
 * Permission handling is out of scope for this project
 */
class MainActivity : AppCompatActivity() {

    private val webView by lazy { findViewById<WebView>(R.id.webView) }
    private val progressChromeClient by lazy {
        ProgressChromeClient()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WebView.setWebContentsDebuggingEnabled(true)

        setUpWebView()
        webView.loadUrl("file:///android_asset/index.html")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.apply {
            clearCache(true)
            webChromeClient = progressChromeClient
            addJavascriptInterface(this@MainActivity, "Android")
        }.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setAppCacheEnabled(false)
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != ProgressChromeClient.REQUEST_CODE_INPUT_FILE || progressChromeClient.mFilePathCallback == null) {
            super.onActivityResult(requestCode, resultCode, data)
            return
        }

        var results: Array<Uri>? = null
        // Check that the response is a good one
        if (resultCode == RESULT_OK) {
            if (data == null) {
                // If there is not data, then we may have taken a photo
                if (progressChromeClient.mCameraPhotoPath != null) {
                    results = arrayOf(Uri.parse(progressChromeClient.mCameraPhotoPath))
                }
            } else {
                val dataString = data.dataString
                if (dataString != null) {
                    results = arrayOf(Uri.parse(dataString))
                }
            }
        }

        progressChromeClient.mFilePathCallback?.onReceiveValue(results)
        progressChromeClient.mFilePathCallback = null
        return
    }


    @JavascriptInterface
    fun handleAction(str: String?) {
    }

    class ProgressChromeClient : WebChromeClient() {

        private var mFileChooserParams: FileChooserParams? = null
        var mFilePathCallback: ValueCallback<Array<Uri>>? = null
        var mCameraPhotoPath: String? = null

        override fun onPermissionRequest(request: PermissionRequest) {
            val perms = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            request.grant(perms)
        }

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            val context = webView?.context ?: return false
            val activity = (context as? Activity) ?: return false
            mFilePathCallback?.onReceiveValue(null)

            mFilePathCallback = filePathCallback
            mFileChooserParams = fileChooserParams

            pickFile(activity)
            return true
        }


        @Suppress("MemberVisibilityCanBePrivate")
        fun pickFile(activity: Activity) {
            mCameraPhotoPath = null
            var takePictureIntent: Intent? = null
            if (
                ActivityCompat.checkSelfPermission(
                    activity, Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                if (takePictureIntent.resolveActivity(activity.packageManager) != null) {
                    // Create the File where the photo should go
                    var photoFile: File? = null
                    try {
                        photoFile = createImageFile()
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath)
                    } catch (e: IOException) {
                        // Error occurred while creating the File
                        e.printStackTrace()
                    }

                    // Continue only if the File was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.absolutePath
                        takePictureIntent.putExtra(
                            MediaStore.EXTRA_OUTPUT,
                            Uri.fromFile(photoFile)
                        )
                    } else {
                        takePictureIntent = null
                    }
                }
            }

            val contentSelectionIntent = mFileChooserParams?.createIntent() ?: return

            val chooserIntent = Intent(Intent.ACTION_CHOOSER)
            chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent)
            chooserIntent.putExtra(Intent.EXTRA_TITLE, mFileChooserParams?.title)
            if (takePictureIntent != null && mFileChooserParams?.acceptTypes?.any {
                    it.contains(
                        "image",
                        true
                    )
                } == true) {
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takePictureIntent))
            }

            ActivityCompat.startActivityForResult(
                activity,
                chooserIntent,
                REQUEST_CODE_INPUT_FILE,
                null
            )
        }

        @Throws(IOException::class)
        private fun createImageFile(): File? {
            // Create an image file name
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_" + timeStamp + "_"
            val storageDir =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",  /* suffix */
                storageDir /* directory */
            )
        }


        companion object {
            private const val TAG = "ProgressChromeClient"

            const val REQUEST_CODE_PERM_INPUT_FILE = 20041
            const val REQUEST_CODE_INPUT_FILE = 10041
        }

    }
}