package com.maunkavach.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.maunkavach.crypto.VaultKeyManager
import com.maunkavach.data.ContactExchange
import com.maunkavach.data.db.ContactDao
import com.maunkavach.data.db.MaunKavachDbHelper
import com.maunkavach.security.BiometricHelper
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanContactQrScreen(
    activity: FragmentActivity,
    currentUsername: String,
    onBack: () -> Unit,
    onContactReady: (String) -> Unit
) {
    val context = LocalContext.current
    val contactDao = remember { ContactDao(MaunKavachDbHelper(context)) }
    val vaultKeyManager = remember { VaultKeyManager(context) }
    var payload by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            error = "Camera permission is required to scan QR codes. You can still paste QR data below."
        }
    }

    fun saveValidatedContact(scannedPayload: String) {
        runCatching {
            val exchange = ContactExchange.parse(scannedPayload)
            if (exchange.username.equals(currentUsername, ignoreCase = false)) {
                throw IllegalArgumentException("This is your own QR. Scan the other person's QR to connect.")
            }
            val contact = ContactExchange.toContact(exchange)
            contactDao.upsert(contact)
            val keyBundle = exchange.keyBundle
            if (keyBundle != null) {
                vaultKeyManager.importFromQr(ContactExchange.fullBundleJson(keyBundle))
            }
            contact.id
        }.onSuccess { contactId ->
            error = null
            onContactReady(contactId)
        }.onFailure {
            importing = false
            error = it.message ?: "Could not import contact."
        }
    }

    fun importContact(scannedPayload: String) {
        if (importing) return
        importing = true
        payload = scannedPayload
        if (!BiometricHelper.canUseBiometrics(activity)) {
            importing = false
            error = "Device PIN, fingerprint, or face unlock is required before adding a QR contact."
            return
        }
        BiometricHelper.promptUnlock(
            activity = activity,
            title = "Add Chat Contact",
            subtitle = "Authenticate before importing chat keys",
            onSuccess = { saveValidatedContact(scannedPayload) },
            onError = {
                importing = false
                error = it
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        },
        containerColor = Color(0xFFF6F1FF)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color.White, tonalElevation = 2.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = Color(0xFF7C3AED))
                    Spacer(Modifier.height(12.dp))
                    Text("Scan contact QR", style = MaterialTheme.typography.titleLarge)
                    Text("Fit the QR inside the frame.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B5B80))
                    Spacer(Modifier.height(18.dp))
                    if (hasCameraPermission) {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(0.78f)) {
                            ContactQrCameraScanner(
                                enabled = !importing,
                                modifier = Modifier.fillMaxSize(),
                                onQrScanned = { scannedPayload ->
                                    error = null
                                    importContact(scannedPayload)
                                },
                                onError = { error = it }
                            )
                            ScannerFrame(modifier = Modifier.align(Alignment.Center).size(260.dp))
                        }
                    } else {
                        Button(
                            onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                            Text("Allow camera and scan")
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF7C3AED))
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = payload,
                        onValueChange = {
                            payload = it
                            error = null
                            importing = false
                        },
                        label = { Text("QR data fallback") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        minLines = 5
                    )
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { importContact(payload) },
                        enabled = payload.isNotBlank() && !importing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create contact and open chat")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun ContactQrCameraScanner(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanLocked = remember { AtomicBoolean(false) }
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        update = { previewView ->
            if (enabled) {
                scanLocked.set(false)
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener(
                {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { imageProxy ->
                                analyzeQrImage(
                                    imageProxy = imageProxy,
                                    scanner = scanner,
                                    enabled = enabled,
                                    scanLocked = scanLocked,
                                    mainExecutor = mainExecutor,
                                    onQrScanned = onQrScanned,
                                    onError = onError
                                )
                            }
                        }

                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis
                        )
                    }.onFailure {
                        onError(it.message ?: "Could not start camera scanner.")
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    )
}

@Composable
private fun ScannerFrame(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
        val corner = 54.dp.toPx()
        val white = Color.White
        drawRect(color = Color.White.copy(alpha = 0.16f), style = Stroke(width = 2.dp.toPx()))
        drawLine(white, Offset.Zero, Offset(corner, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset.Zero, Offset(0f, corner), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(size.width, 0f), Offset(size.width - corner, 0f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(size.width, 0f), Offset(size.width, corner), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(0f, size.height), Offset(corner, size.height), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(0f, size.height), Offset(0f, size.height - corner), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(size.width, size.height), Offset(size.width - corner, size.height), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(white, Offset(size.width, size.height), Offset(size.width, size.height - corner), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@ExperimentalGetImage
private fun analyzeQrImage(
    imageProxy: ImageProxy,
    scanner: BarcodeScanner,
    enabled: Boolean,
    scanLocked: AtomicBoolean,
    mainExecutor: java.util.concurrent.Executor,
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (!enabled || scanLocked.get() || mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(image)
        .addOnSuccessListener(mainExecutor) { barcodes ->
            val rawValue = barcodes.firstNotNullOfOrNull { it.rawValue }?.trim()
            if (!rawValue.isNullOrBlank() && scanLocked.compareAndSet(false, true)) {
                onQrScanned(rawValue)
            }
        }
        .addOnFailureListener(mainExecutor) {
            onError(it.message ?: "Could not read QR code.")
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
