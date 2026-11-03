package com.example.customdialer.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Entity(tableName = "code_images")
data class CodeImage(
    @PrimaryKey val code: String,
    val imageUri: String
)

@Dao
interface CodeImageDao {
    @Query("SELECT * FROM code_images")
    fun getAllCodes(): Flow<List<CodeImage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(codeImage: CodeImage)

    @Delete
    suspend fun deleteCode(codeImage: CodeImage)
}

@Database(entities = [CodeImage::class], version = 1, exportSchema = false)
abstract class DialerDatabase : RoomDatabase() {
    abstract fun codeImageDao(): CodeImageDao

    companion object {
        @Volatile
        private var INSTANCE: DialerDatabase? = null

        fun getDatabase(context: Context): DialerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DialerDatabase::class.java,
                    "dialer_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class DialerViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = DialerDatabase.getDatabase(application).codeImageDao()

    val savedCodes: StateFlow<List<CodeImage>> = dao.getAllCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dialedNumber = MutableStateFlow("")
    val dialedNumber: StateFlow<String> = _dialedNumber.asStateFlow()

    private val _activeImageUri = MutableStateFlow<String?>(null)
    val activeImageUri: StateFlow<String?> = _activeImageUri.asStateFlow()

    fun onKeyPressed(char: String) {
        val updated = _dialedNumber.value + char
        _dialedNumber.value = updated
        val match = savedCodes.value.find { it.code == updated }
        if (match != null) {
            _activeImageUri.value = match.imageUri
        }
    }

    fun onDeletePressed() {
        if (_dialedNumber.value.isNotEmpty()) {
            _dialedNumber.value = _dialedNumber.value.dropLast(1)
        }
    }

    fun dismissImage() {
        _activeImageUri.value = null
        _dialedNumber.value = ""
    }

    fun saveCodeMapping(code: String, uri: Uri) {
        val context = getApplication<Application>()
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        viewModelScope.launch {
            dao.insertCode(CodeImage(code = code.trim(), imageUri = uri.toString()))
        }
    }

    fun deleteCodeMapping(codeImage: CodeImage) {
        viewModelScope.launch {
            dao.deleteCode(codeImage)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialerScreen(viewModel: DialerViewModel) {
    val context = LocalContext.current
    val dialedNumber by viewModel.dialedNumber.collectAsState()
    val activeImageUri by viewModel.activeImageUri.collectAsState()
    var showSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Teléfono") },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Configurar")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Text(
                text = dialedNumber,
                fontSize = 36.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            val buttons = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("*", "0", "#")
            )

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                buttons.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { symbol ->
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.onKeyPressed(symbol) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(symbol, fontSize = 28.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.size(56.dp))
                FloatingActionButton(
                    onClick = {
                        if (dialedNumber.isNotBlank()) {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${Uri.encode(dialedNumber)}")
                            }
                            context.startActivity(intent)
                        }
                    },
                    containerColor = Color(0xFF2E7D32),
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Llamar", tint = Color.White)
                }

                IconButton(
                    onClick = { viewModel.onDeletePressed() },
                    modifier = Modifier.size(56.dp)
                ) {
                    if (dialedNumber.isNotEmpty()) {
                        Icon(Icons.Default.Backspace, contentDescription = "Borrar")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        activeImageUri?.let { uriStr ->
            Dialog(
                onDismissRequest = { viewModel.dismissImage() },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = Uri.parse(uriStr),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { viewModel.dismissImage() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                }
            }
        }

        if (showSettingsDialog) {
            val savedCodes by viewModel.savedCodes.collectAsState()
            var inputCode by remember { mutableStateOf("") }
            var selectedUri by remember { mutableStateOf<Uri?>(null) }

            val picker = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri -> selectedUri = uri }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("Asignar Imágenes") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it },
                            label = { Text("Código (ej: *123#)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { picker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (selectedUri != null) "Foto lista ✓" else "Elegir foto")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                selectedUri?.let { uri ->
                                    if (inputCode.isNotBlank()) {
                                        viewModel.saveCodeMapping(inputCode, uri)
                                        inputCode = ""
                                        selectedUri = null
                                    }
                                }
                            },
                            enabled = inputCode.isNotBlank() && selectedUri != null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Guardar combinación")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(modifier = Modifier.height(120.dp)) {
                            items(savedCodes) { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(item.code, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.deleteCodeMapping(item) }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettingsDialog = false }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}
