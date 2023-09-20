package com.dupaorg.memefinder

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dupaorg.memefinder.ui.theme.MemeFinderTheme
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import info.debatty.java.stringsimilarity.WeightedLevenshtein
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        requestPermission(
            permission,
            onDenied = {
                setContent {
                    MemeFinderTheme {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            PermissionDenied(permission)
                        }
                    }
                }
            }
        ) {
            setContent {
                MemeFinderTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val wl = WeightedLevenshtein { c1, c2 ->
                            if (c1 == 'R' && c2 == 'P') 0.1
                            else if (c1 == 'E' && c2 == 'F') 0.1 else 1.0
                        }
                        val imagesRepository = ImagesRepository()
                        val context = LocalContext.current.applicationContext
                        App(imagesRepository.all(context)) { s1, s2 -> wl.distance(s1, s2) }
                    }
                }
            }
        }
    }

    private fun requestPermission(
        permission: String,
        onDenied: () -> Unit,
        onGranted: () -> Unit,
    ) {
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    onGranted()
                } else {
                    onDenied()
                }
            }
            .launch(permission)
    }
}

@Composable
private fun App(images: Flow<RawImage>, distance: (String, String) -> Double) {
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val texts = remember { mutableStateListOf<ProcessedImage>() }
    suspend fun index() {
        images.collect { image ->
            recognizer
                .process(image.data)
                .addOnSuccessListener { recognized ->
                    texts.add(ProcessedImage(recognized.text, image.path, image.name))
                }
                .addOnFailureListener { e ->
                    when (e) {
                        is MlKitException ->
                            Log.e("RECOGNIZER", "text recognition failed: ${e.errorCode}")
                        else -> Log.e("RECOGNIZER", "other error: ${e.message}")
                    }
                }
        }
    }
    // val context = LocalContext.current.applicationContext
    Column(
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
        // modifier = Modifier.navigationBarsPadding()
    ) {
        ImageList(texts)
        Spacer(modifier = Modifier.width(50.dp))
        SearchBox { searchTerm ->
            if (texts.isEmpty()) {
                CoroutineScope(Dispatchers.Main).launch { index() }
            } else {
                texts.sortBy { distance(searchTerm, it.recognizedText) }
            }
        }
    }
}

@Composable
fun ImageList(images: List<ProcessedImage>) {
    when {
        // TODO: handle this case better
        images.isEmpty() -> Text("empty list")
        else -> {
            LazyVerticalGrid(columns = GridCells.Fixed(count = 3), reverseLayout = true) {
                items(images) { image ->
                    Column(
                        modifier =
                            Modifier.border(
                                    width = Dp.Hairline,
                                    shape = RectangleShape,
                                    brush = SolidColor(Color.Red)
                                )
                                .padding(2.dp)
                    ) {
                        if (image.recognizedText.isBlank()) {
                            Text("no text found")
                        } else {
                            Text(
                                image.recognizedText,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        AsyncImage(
                            modifier = Modifier.fillMaxSize(),
                            model = image.path,
                            contentDescription = image.name
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDenied(permission: String) {
    Text("Permission $permission denied")
}

data class RawImage(val name: String, val path: Uri, val data: InputImage)

data class ProcessedImage(val recognizedText: String, val path: Uri, val name: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBox(search: (String) -> Unit) {
    var input by remember { mutableStateOf("") }

    //    val context = LocalContext.current.applicationContext
    //    val focusManager = LocalFocusManager.current
    //    val keyboardController = LocalSoftwareKeyboardController.current
    TextField(
        modifier = Modifier.fillMaxWidth().padding(all = 10.dp),
        shape = RoundedCornerShape(15.dp),
        value = input,
        onValueChange = { input = it },
        label = { Text("search") },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = "search icon")
        },
        trailingIcon = {
            IconButton(onClick = { input = "" }) {
                Icon(Icons.Outlined.Clear, contentDescription = "clear input")
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    // close the keyboard
                    // keyboardController?.hide()
                    // clear focus
                    // focusManager.clearFocus()
                    search(input)
                }
            )
    )
}

@Preview(showBackground = true)
@Composable
private fun DefaultPreview() {
    ImageList(
        images =
            listOf(
                ProcessedImage(
                    "dupa",
                    Uri.parse("content://media/external/images/media/27"),
                    "dupa"
                ),
                ProcessedImage(
                    "dupa",
                    Uri.parse("content://media/external/images/media/26"),
                    "dupa"
                ),
                ProcessedImage(
                    "dupa",
                    Uri.parse("content://media/external/images/media/25"),
                    "dupa"
                ),
            )
    )
}
