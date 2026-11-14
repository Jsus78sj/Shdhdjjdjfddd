package com.abdullah.daftar.ui.pdf_viewer

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abdullah.daftar.data.local.entity.HighlightEntity
import com.abdullah.daftar.data.text.ArabicTextNormalizer
import com.abdullah.daftar.domain.model.HighlightColor
import com.abdullah.daftar.domain.model.PdfExtractionError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewerViewModel,
    onNavigateBack: () -> Unit
) {
    val pdfFile by viewModel.pdfFileState.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarksState.collectAsStateWithLifecycle()
    val highlights by viewModel.highlightsState.collectAsStateWithLifecycle()
    val inFileSearchResults by viewModel.inFileSearchResults.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }
    var initialScrollRestored by remember { mutableStateOf(false) }

    var showJumpDialog by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showBookmarksListDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var showInFileSearchDialog by remember { mutableStateOf(false) }

    // المرحلة 5: وضع التظليل وألوانه
    var isHighlightMode by remember { mutableStateOf(false) }
    var selectedHighlightColor by remember { mutableLongStateOf(HighlightColor.YELLOW.colorHex) }

    var scale by remember { mutableFloatStateOf(1f) }
    val horizontalScrollState = rememberScrollState()
    var pendingScrollX by remember { mutableIntStateOf(-1) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    // صفحة آخر تظليل: عليها يعمل "تراجع/مسح" بدل الصفحة الظاهرة أولاً في القائمة
    var lastHighlightPage by remember { mutableStateOf<Int?>(null) }

    val lazyListState = rememberLazyListState()
    val renderMutex = remember { Mutex() }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // عرض الصورة = عرض الشاشة الفعلي بالبكسل (بدون مضاعفة) لتفادي OOM.
    // السقف 1440 يحمي الأجهزة اللوحية من صفحات ضخمة.
    val targetWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx().coerceIn(720, 1440) }
    }

    // دقة العرض تتبع التكبير على درجات (1x/2x/3x) وبسقف مرتبط بالذاكرة كي لا تتشوش الصفحة عند التكبير
    // ولا تتجاوز صفحة واحدة ثلث الكاش (نسبة ارتفاع/عرض ≤ 1.5 تقريباً، 4 بايت للبكسل)
    val maxRenderWidthPx = remember(targetWidthPx) {
        val pageBudgetBytes = Runtime.getRuntime().maxMemory() / 8 / 3
        sqrt(pageBudgetBytes / (4.0 * 1.5)).toInt().coerceIn(targetWidthPx, 2400)
    }
    val renderScale = when {
        scale <= 1.25f -> 1
        scale <= 2f -> 2
        else -> 3
    }
    val renderWidthPx = (targetWidthPx * renderScale).coerceAtMost(maxRenderWidthPx)

    // كاش محدّد بالبايت (1/8 من ذاكرة التطبيق) بدلاً من عدد صفحات ثابت
    val bitmapCache = remember(renderWidthPx) {
        val maxBytes = (Runtime.getRuntime().maxMemory() / 8)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        object : LruCache<Int, Bitmap>(maxBytes) {
            override fun sizeOf(key: Int, value: Bitmap): Int = value.allocationByteCount
        }
    }

    val rendererHolder = remember(pdfFile?.internalPath) {
        val path = pdfFile?.internalPath ?: return@remember null
        val file = File(path)
        if (!file.exists()) return@remember null
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            pfd to renderer
        }.getOrNull()
    }

    DisposableEffect(rendererHolder) {
        pageCount = rendererHolder?.second?.pageCount ?: 0
        onDispose {
            runCatching {
                rendererHolder?.second?.close()
                rendererHolder?.first?.close()
            }
        }
    }

    // حفظ التقدّم فوراً عند onPause وعند مغادرة الشاشة؛ والودجت يُحدَّث مرة واحدة عند المغادرة فقط
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) viewModel.flushReadingProgress()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flushReadingProgress(refreshWidget = true)
        }
    }

    // يطبّق موضع التمرير الأفقي بعد أن يتحدث عرض المحتوى (maxValue) إثر تغيّر التكبير
    LaunchedEffect(horizontalScrollState.maxValue) {
        if (pendingScrollX >= 0) {
            horizontalScrollState.scrollTo(pendingScrollX)
            pendingScrollX = -1
        }
    }

    // التكبير بتغيير عرض الصفحات (لا graphicsLayer): لا قصّ للحواف، والتمرير الرأسي طبيعي، والأفقي عبر horizontalScroll
    fun setZoom(requestedScale: Float, focusX: Float) {
        val old = scale
        val new = requestedScale.coerceIn(1f, MAX_ZOOM)
        if (new == old) return
        pendingScrollX = ((horizontalScrollState.value + focusX) * (new / old) - focusX)
            .roundToInt().coerceAtLeast(0)
        scale = new
    }

    LaunchedEffect(pdfFile?.id, pageCount) {
        if (!initialScrollRestored && pageCount > 0 && pdfFile != null) {
            val startPage = if (viewModel.requestedInitialPage >= 0) {
                viewModel.requestedInitialPage
            } else {
                pdfFile?.lastReadPage ?: 0
            }
            val target = startPage.coerceIn(0, pageCount - 1)
            lazyListState.scrollToItem(target)
            currentPage = target
            initialScrollRestored = true
        }
    }

    LaunchedEffect(lazyListState, pageCount) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { visibleIndex ->
                if (pageCount > 0 && initialScrollRestored) {
                    currentPage = visibleIndex
                    viewModel.saveReadingProgress(visibleIndex, pageCount)
                }
            }
    }

    val currentBookmark = bookmarks.find { it.pageIndex == currentPage }
    val highlightTargetPage = lastHighlightPage ?: currentPage

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pdfFile?.displayName ?: "عارض PDF",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (pageCount > 0) {
                            Text(
                                text = "صفحة ${currentPage + 1} من $pageCount",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                },
                actions = {
                    // الظاهر: بحث، تظليل، إشارة مرجعية؛ والباقي في قائمة ⋮
                    IconButton(
                        onClick = { showInFileSearchDialog = true },
                        enabled = pageCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "البحث داخل الملف"
                        )
                    }
                    IconButton(
                        onClick = {
                            isHighlightMode = !isHighlightMode
                            lastHighlightPage = null
                        },
                        enabled = pageCount > 0
                    ) {
                        Icon(
                            imageVector = Icons.Default.BorderColor,
                            contentDescription = "وضع التظليل",
                            tint = if (isHighlightMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            if (currentBookmark != null) {
                                viewModel.removeBookmark(currentBookmark)
                            } else {
                                showAddBookmarkDialog = true
                            }
                        },
                        enabled = pageCount > 0
                    ) {
                        Icon(
                            imageVector = if (currentBookmark != null) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "إشارة مرجعية لهذه الصفحة",
                            tint = if (currentBookmark != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "المزيد")
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            val isFav = pdfFile?.isFavorite == true
                            DropdownMenuItem(
                                text = { Text(if (isFav) "إزالة من المفضلة" else "إضافة إلى المفضلة") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                        contentDescription = null,
                                        tint = if (isFav) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.toggleFavorite()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("قائمة الإشارات المرجعية") },
                                leadingIcon = { Icon(Icons.Default.Bookmarks, contentDescription = null) },
                                enabled = pageCount > 0,
                                onClick = {
                                    showOverflowMenu = false
                                    showBookmarksListDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("إضافة ملاحظة لهذه الصفحة") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                                enabled = pageCount > 0,
                                onClick = {
                                    showOverflowMenu = false
                                    showQuickNoteDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("الانتقال إلى صفحة") },
                                leadingIcon = { Icon(Icons.Default.FindInPage, contentDescription = null) },
                                enabled = pageCount > 0,
                                onClick = {
                                    showOverflowMenu = false
                                    showJumpDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // شريط أدوات التظليل عند تفعيله
            if (isHighlightMode) {
                Surface(
                    tonalElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "لون التظليل:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                HighlightColor.entries.forEach { hlColor ->
                                    val selected = selectedHighlightColor == hlColor.colorHex
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(hlColor.colorHex).copy(alpha = 0.9f))
                                            .border(
                                                width = if (selected) 2.dp else 0.dp,
                                                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedHighlightColor = hlColor.colorHex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = hlColor.arabicLabel,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // تراجع/مسح يعملان على صفحة آخر تظليل (أو الصفحة الحالية إن لم يُرسم شيء بعد)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ص ${highlightTargetPage + 1}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                IconButton(
                                    onClick = { viewModel.undoLastHighlightOnPage(highlightTargetPage) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Undo,
                                        contentDescription = "تراجع عن آخر تظليل"
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.clearPageHighlights(highlightTargetPage) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "مسح تظليلات الصفحة",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        Text(
                            text = "اضغط مطولاً على الصفحة ثم اسحب للتظليل؛ والتمرير بإصبع واحد يعمل كالمعتاد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .pointerInput(isHighlightMode) {
                        if (!isHighlightMode) {
                            // قرص بإصبعين فقط؛ التمرير بإصبع واحد يبقى للقائمة (رأسياً) وللتمرير الأفقي
                            detectPinchZoom { zoom, focusX -> setZoom(scale * zoom, focusX) }
                        }
                    }
                    .pointerInput(isHighlightMode) {
                        if (!isHighlightMode) {
                            // نقر مزدوج: تبديل بين 1x و2x
                            detectTapGestures(
                                onDoubleTap = { offset ->
                                    setZoom(if (scale > 1.05f) 1f else 2f, offset.x)
                                }
                            )
                        }
                    }
            ) {
                val viewportWidth = maxWidth
                val viewportWidthPx = constraints.maxWidth.toFloat()
                val renderer = rendererHolder?.second
                if (renderer == null || pageCount == 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("تعذر فتح ملف PDF أو أن الملف غير موجود.")
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        LazyColumn(
                            state = lazyListState,
                            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .width(viewportWidth * scale)
                                .fillMaxHeight()
                        ) {
                            items(count = pageCount, key = { it }) { pageIndex ->
                                val pageHighlights = remember(highlights, pageIndex) {
                                    highlights.filter { it.pageIndex == pageIndex }
                                }
                                PdfPageItem(
                                    pageIndex = pageIndex,
                                    renderer = renderer,
                                    renderMutex = renderMutex,
                                    bitmapCache = bitmapCache,
                                    targetWidthPx = renderWidthPx,
                                    pageHighlights = pageHighlights,
                                    isHighlightMode = isHighlightMode,
                                    activeHighlightColorHex = selectedHighlightColor,
                                    onAddHighlight = { sx, sy, ex, ey ->
                                        lastHighlightPage = pageIndex
                                        viewModel.addHighlight(
                                            pageIndex = pageIndex,
                                            startX = sx,
                                            startY = sy,
                                            endX = ex,
                                            endY = ey,
                                            colorHex = selectedHighlightColor
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // شريط سفلي عائم
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { setZoom(scale - 0.25f, viewportWidthPx / 2f) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOut,
                                    contentDescription = "تصغير",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                            Text(
                                text = "ص ${currentPage + 1} / $pageCount (${(scale * 100).toInt()}%)",
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            IconButton(
                                onClick = { setZoom(scale + 0.25f, viewportWidthPx / 2f) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "تكبير",
                                    tint = MaterialTheme.colorScheme.inverseOnSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // نافذة البحث داخل محتوى الملف الحالي (المرحلة 5)
    if (showInFileSearchDialog) {
        var searchQuery by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                viewModel.setInFileSearchQuery("")
                showInFileSearchDialog = false
            },
            title = { Text("البحث داخل محتوى هذا الملف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.setInFileSearchQuery(it)
                        },
                        label = { Text("اكتب الكلمة أو العبارة...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val extractionError = pdfFile?.extractionError
                    if (extractionError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = PdfExtractionError.message(extractionError),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            if (extractionError != PdfExtractionError.PASSWORD) {
                                TextButton(onClick = { viewModel.triggerReindexFile() }) {
                                    Text("إعادة المحاولة")
                                }
                            }
                        }
                    } else if (pdfFile?.isTextExtracted == false) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "لم تكتمل فهرسة نص هذا الملف بعد.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.triggerReindexFile() }) {
                                Text("فهرسة الآن")
                            }
                        }
                    }

                    if (searchQuery.isNotBlank()) {
                        if (inFileSearchResults.isEmpty()) {
                            Text(
                                text = "لا توجد صفحات مطابقة لعبارة البحث.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.height(240.dp)
                            ) {
                                items(inFileSearchResults, key = { it.id }) { pageMatch ->
                                    Surface(
                                        tonalElevation = 2.dp,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    val target = pageMatch.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                                    lazyListState.scrollToItem(target)
                                                    currentPage = target
                                                    viewModel.saveReadingProgress(target, pageCount)
                                                }
                                                showInFileSearchDialog = false
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                text = "صفحة ${pageMatch.pageIndex + 1}",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = extractSnippet(pageMatch.pageText, searchQuery),
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setInFileSearchQuery("")
                        showInFileSearchDialog = false
                    }
                ) {
                    Text("إغلاق")
                }
            }
        )
    }

    // نافذة إضافة إشارة مرجعية للصفحة الحالية
    if (showAddBookmarkDialog) {
        var bookmarkTitle by remember { mutableStateOf("صفحة ${currentPage + 1}") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("إضافة إشارة مرجعية (ص ${currentPage + 1})") },
            text = {
                OutlinedTextField(
                    value = bookmarkTitle,
                    onValueChange = { bookmarkTitle = it },
                    label = { Text("عنوان الإشارة المرجعية") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addBookmark(currentPage, bookmarkTitle)
                        showAddBookmarkDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة عرض جميع الإشارات المرجعية داخل الملف
    if (showBookmarksListDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksListDialog = false },
            title = { Text("الإشارات المرجعية (${bookmarks.size})") },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("لا توجد إشارات مرجعية محفوظة في هذا الملف بعد.")
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(260.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bm ->
                            Surface(
                                tonalElevation = 2.dp,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            val target = bm.pageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                            lazyListState.scrollToItem(target)
                                            currentPage = target
                                            viewModel.saveReadingProgress(target, pageCount)
                                        }
                                        showBookmarksListDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = bm.title,
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Text(
                                            text = "صفحة ${bm.pageIndex + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeBookmark(bm) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksListDialog = false }) {
                    Text("إغلاق")
                }
            }
        )
    }

    // نافذة إضافة ملاحظة مرتبطة بهذه الصفحة مباشرة
    if (showQuickNoteDialog) {
        var noteTitle by remember { mutableStateOf("ملاحظة على صفحة ${currentPage + 1}") }
        var noteContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showQuickNoteDialog = false },
            title = { Text("ملاحظة مرتبطة بصفحة ${currentPage + 1}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = noteTitle,
                        onValueChange = { noteTitle = it },
                        label = { Text("عنوان الملاحظة") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = noteContent,
                        onValueChange = { noteContent = it },
                        label = { Text("محتوى الملاحظة") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addNoteForCurrentPage(currentPage, noteTitle, noteContent)
                        showQuickNoteDialog = false
                    },
                    enabled = noteTitle.isNotBlank() || noteContent.isNotBlank()
                ) {
                    Text("حفظ في ملاحظات المادة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickNoteDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // نافذة الانتقال إلى صفحة محددة
    if (showJumpDialog && pageCount > 0) {
        var pageInput by remember { mutableStateOf((currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("الانتقال إلى صفحة") },
            text = {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("رقم الصفحة (1 - $pageCount)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetPage = (pageInput.toIntOrNull() ?: 1).coerceIn(1, pageCount) - 1
                        coroutineScope.launch {
                            lazyListState.scrollToItem(targetPage)
                            currentPage = targetPage
                            viewModel.saveReadingProgress(targetPage, pageCount)
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("انتقال")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun PdfPageItem(
    pageIndex: Int,
    renderer: PdfRenderer,
    renderMutex: Mutex,
    bitmapCache: LruCache<Int, Bitmap>,
    targetWidthPx: Int,
    pageHighlights: List<HighlightEntity>,
    isHighlightMode: Boolean,
    activeHighlightColorHex: Long,
    onAddHighlight: (startX: Float, startY: Float, endX: Float, endY: Float) -> Unit
) {
    // تبقى الصورة القديمة معروضة حتى تجهز الأوضح عند تغيّر دقة العرض (لا وميض)
    var bitmap by remember(pageIndex) { mutableStateOf(bitmapCache.get(pageIndex)) }
    var aspectRatio by remember(pageIndex) { mutableFloatStateOf(0.707f) }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(pageIndex, renderer, bitmapCache) {
        val cached = bitmapCache.get(pageIndex)
        if (cached == null) {
            val rendered = withContext(Dispatchers.IO) {
                renderMutex.withLock {
                    runCatching {
                        renderer.openPage(pageIndex).use { page ->
                            val ratio = page.width.toFloat() / page.height.toFloat().coerceAtLeast(1f)
                            aspectRatio = ratio
                            val heightPx = (targetWidthPx / ratio).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(
                                targetWidthPx,
                                heightPx,
                                Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmapCache.put(pageIndex, bmp)
                            bmp
                        }
                    }.getOrNull()
                }
            }
            if (rendered != null) bitmap = rendered
        } else {
            bitmap = cached
            aspectRatio = cached.width.toFloat() / cached.height.toFloat().coerceAtLeast(1f)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isHighlightMode, activeHighlightColorHex) {
                    if (isHighlightMode) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                dragStart = offset
                                dragCurrent = offset
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                dragCurrent = change.position
                            },
                            onDragEnd = {
                                val start = dragStart
                                val end = dragCurrent
                                if (start != null && end != null && size.width > 0 && size.height > 0) {
                                    val sx = (start.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val sy = (start.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    val ex = (end.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    val ey = (end.y / size.height.toFloat()).coerceIn(0f, 1f)
                                    if (kotlin.math.abs(ex - sx) > 0.01f || kotlin.math.abs(ey - sy) > 0.005f) {
                                        onAddHighlight(sx, sy, ex, ey)
                                    }
                                }
                                dragStart = null
                                dragCurrent = null
                            },
                            onDragCancel = {
                                dragStart = null
                                dragCurrent = null
                            }
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val currentBitmap = bitmap
            if (currentBitmap != null) {
                Image(
                    bitmap = currentBitmap.asImageBitmap(),
                    contentDescription = "صفحة ${pageIndex + 1}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize()
                )

                // رسم التظليلات المحفوظة والتظليل الجاري رسمه حالياً
                Canvas(modifier = Modifier.fillMaxSize()) {
                    pageHighlights.forEach { hl ->
                        val left = hl.startX * size.width
                        val top = hl.startY * size.height
                        val right = hl.endX * size.width
                        val bottom = hl.endY * size.height
                        val rectHeight = (bottom - top).coerceAtLeast(18.dp.toPx())
                        drawRect(
                            color = Color(hl.colorHex),
                            topLeft = Offset(left, top),
                            size = Size((right - left).coerceAtLeast(12.dp.toPx()), rectHeight)
                        )
                    }

                    val start = dragStart
                    val end = dragCurrent
                    if (start != null && end != null) {
                        val left = minOf(start.x, end.x)
                        val top = minOf(start.y, end.y)
                        val width = kotlin.math.abs(end.x - start.x).coerceAtLeast(12.dp.toPx())
                        val height = kotlin.math.abs(end.y - start.y).coerceAtLeast(18.dp.toPx())
                        drawRect(
                            color = Color(activeHighlightColorHex),
                            topLeft = Offset(left, top),
                            size = Size(width, height)
                        )
                    }
                }
            } else {
                CircularProgressIndicator()
            }
        }
    }
}

private fun extractSnippet(fullText: String, query: String): String =
    ArabicTextNormalizer.snippet(fullText, query, charsBefore = 40, charsAfter = 60)

private const val MAX_ZOOM = 3f

/** قرص بإصبعين فقط (يُعالج في المرحلة الأولية ويستهلك الحدث): التمرير بإصبع واحد لا يتأثر. */
private suspend fun PointerInputScope.detectPinchZoom(onZoom: (zoom: Float, focusX: Float) -> Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size >= 2 && event.changes.all { it.pressed }) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    onZoom(zoom, event.calculateCentroid().x)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
