package tj.itservice.identifycardcheker

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import tj.itservice.identifycardcheker.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = loadBitmapFromUri(photoUri)
            if (bitmap == null) showToast("Не удалось загрузить фото")
            else checkSide(bitmap)
        }
    }

    enum class CardSide { FRONT, BACK,FACE }
    private var currentSide = CardSide.FRONT
    private var photoUri: Uri? = null
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.firstSide.setOnClickListener { openCameraIntent(CardSide.FRONT) }
        binding.secondSide.setOnClickListener { openCameraIntent(CardSide.BACK) }
        binding. faceSide.setOnClickListener { openCameraIntent(CardSide.FACE) }
    }

    @Suppress("DEPRECATION")
    private fun loadBitmapFromUri(uri: Uri?) = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri!!))
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    private fun openCameraIntent(side: CardSide) {
        currentSide = side
        val photoFile = File(getExternalFilesDir("Pictures"), "face_photo.jpg")
        photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        cameraLauncher.launch(intent)
    }


    private fun checkFace(bitmap: Bitmap,onResult:(List<Face>)  -> Unit= {}) {
        val image = InputImage.fromBitmap(bitmap, 0)

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()


        val detector = FaceDetection.getClient(options)

        val detectorProcess = detector.process(image)
        detectorProcess.addOnSuccessListener { faces ->
            onResult.invoke(faces)
        }
        detectorProcess.addOnFailureListener {
            onResult.invoke(mutableListOf())
        }
    }

    private fun recognizeText(bitmap: Bitmap,keywords: List<String> = listOf(), onResult: (Boolean) -> Unit = {}) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val options = TextRecognizerOptions.Builder().build()
        val recognizer = TextRecognition.getClient(options)

        val detectorProcess =  recognizer.process(image)
        detectorProcess.addOnSuccessListener { visionText ->
            val found = keywords.all { keyword ->  visionText.text.lowercase().contains(keyword) }
            onResult.invoke(found)
        }
        detectorProcess.addOnFailureListener {
            onResult.invoke(false)
        }
    }

    private fun checkSide(bitmap: Bitmap) = when (currentSide) {
        CardSide.FRONT -> checkFace(bitmap) { faces ->
            if (faces.isEmpty()) {
                showToast("❌ Лицо не обнаружено")
                return@checkFace
            }
            val keywords = listOf("identity card", "republic of tajikistan", "national id")
            recognizeText(bitmap, keywords) { found ->
                if (found) showToast("✅ Паспорт валидный")
                else showToast("❌ Текст ID-карты не найден")
            }
        }

        CardSide.BACK -> recognizeText(bitmap, listOf("address", "idtjk")) { found ->
            if (found) showToast("✅ Паспорт валидный")
            else showToast("❌ Текст ID-карты не найден")
        }

        CardSide.FACE -> checkFace(bitmap) { faces ->
            if (faces.count() > 1) showToast("✅ Лицо обнаружено")
            else showToast("❌ Лицо не обнаружено")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}