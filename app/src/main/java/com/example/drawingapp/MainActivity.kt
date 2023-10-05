package com.example.drawingapp

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {

    private var drawingView: DrawingView? = null
    private var mImageButtonCurrentPaint : ImageButton? = null

    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult())
        {
            result ->
            if (result.resultCode == RESULT_OK && result.data != null)
            {
                val imageBackground : ImageView = findViewById(R.id.iv_background)
                imageBackground.setImageURI(result.data?.data)
            }
        }

    private val requestPermission : ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions())
        {
            permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if(isGranted) {
                    Toast.makeText(this, "Permission granted now you can read storage files $$", Toast.LENGTH_SHORT).show()

                    //we can move from one activity to other and also from one app to other using intents
                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                    openGalleryLauncher.launch(pickIntent)
                }
                else
                {
                    // here this manifest is of android and not from java or any other class
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE)
                        Toast.makeText(this, "Permission Denied $$", Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        /*
        we have created our own view named Drawingview, just like
        other predefined views like button view , text view etc
         */
        drawingView = findViewById(R.id.drawing_view)
        drawingView?.setSizeForBrush(10.toFloat())

        val linearLayoutPaintColors = findViewById<LinearLayout>(R.id.ll_paint_colors)
        mImageButtonCurrentPaint = linearLayoutPaintColors[1] as ImageButton //as inside of linear layout we can have different types of buttons or views also

        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        val ibBrush : ImageButton = findViewById(R.id.ib_brush)
        ibBrush.setOnClickListener {
            showBrushSizeChooserDialog()
        }

        val ibGallery : ImageButton = findViewById(R.id.ib_gallery)
        ibGallery.setOnClickListener {
            requestStoragePermission()
        }

        val ibUndo : ImageButton = findViewById(R.id.ib_undo)
        ibUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }

        val ibRedo : ImageButton = findViewById(R.id.ib_redo)
        ibRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }

        val ibSave : ImageButton = findViewById(R.id.ib_save)
        ibSave.setOnClickListener {
            if(isReadStorageAllowed())
            {
                lifecycleScope.launch {
                    val flDrawingView : FrameLayout = findViewById(R.id.fl_drawing_view_container)

                    saveBitmapFile ( getBitmapFromView ( flDrawingView) )
                }
            }

        }

    }

    private fun requestStoragePermission()
    {
        if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE))
        {
            showRationaleDialog("Kids Drawing App","Kids drawing app needs access to external storage")
        }
        else
        {
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }

    //in new versions right to read also gives right to write
    private fun isReadStorageAllowed() : Boolean
    {
        val result = ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)

        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun showBrushSizeChooserDialog()
    {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush Size: ")

        val smallBtn = brushDialog.findViewById<ImageButton>(R.id.ib_small_brush)
        // we could have written like this also
        //val smallBtn : ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        smallBtn.setOnClickListener{
            //Toast.makeText(this, "working", Toast.LENGTH_SHORT).show()
            drawingView?.setSizeForBrush(7.toFloat())
            brushDialog.dismiss()

        }

        val mediumBtn = brushDialog.findViewById<ImageButton>(R.id.ib_medium_brush)
        mediumBtn.setOnClickListener{
            drawingView?.setSizeForBrush(15.toFloat())
            brushDialog.dismiss()
        }

        val largeBtn = brushDialog.findViewById<ImageButton>(R.id.ib_large_brush)
        largeBtn.setOnClickListener{
            drawingView?.setSizeForBrush(25.toFloat())
            brushDialog.dismiss()
        }

        brushDialog.show()
    }

    fun paintClicked(view: View)
    {
        //Toast.makeText(this, "clicked paint", Toast.LENGTH_SHORT).show()
        if(view != mImageButtonCurrentPaint)
        {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            drawingView?.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint!!.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }

    }

    /*

    //since we cannot save views but we can save bitmaps so
    //we create bitmap from view to save it

    //bit map is combination of
    // background (background of view),
    // view (thing that contains our canvas) and
    // canvas (where we have all of our colors)


     */
    private fun getBitmapFromView(view: View) : Bitmap
    {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

        // now we want to bind the canvas that is on the view
        val canvas = Canvas(returnedBitmap)

        //yahan humne background ko canvas me daal diya
        val bgDrawable = view.background
        if(bgDrawable != null)
        {
            bgDrawable.draw(canvas)
        }
        else
        {
            canvas.drawColor(Color.WHITE)
        }


        //we draw the view on the canvas
        //yahan humne view ko canvas me daal diya
        view.draw(canvas)
        return returnedBitmap

    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?) : String
    {
        var result = ""
        withContext(Dispatchers.IO)
        {
            if(mBitmap != null)
            {
                try {
                    val bytes = ByteArrayOutputStream()
                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    //location for file
                    val f = File(externalCacheDir?.absoluteFile.toString() +
                            File.separator  + "KidsDrawingApp_" +
                            System.currentTimeMillis()/1000 + ".png")

                    val fo = FileOutputStream(f)
                    fo.write(bytes.toByteArray())
                    fo.close()

                    result = f.absolutePath

                    runOnUiThread {
                        if(result.isNotEmpty())
                        {
                            Toast.makeText(this@MainActivity, "file successfully saved to $result", Toast.LENGTH_SHORT).show()
                        }
                        else
                        {
                            Toast.makeText(this@MainActivity, "Something went wrong", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                catch (e : Exception) {
                    result = ""
                    e.printStackTrace()

                }

            }
        }

        return  result

    }

    private fun showRationaleDialog(title: String, message: String, )
    {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

}