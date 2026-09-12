package ephyra.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ephyra.core.archive.EpubChapter

/**
 * Modern Compose-based text reader for Novels and Books.
 * Supports Table of Contents navigation, dual paging modes (continuous vertical scroll
 * and horizontal chapter paging), font customization, and immersive reading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReaderScreen(
    title: String,
    bookUrl: String,
    initialChapterIndex: Int = 0,
    onNavigateBack: () -> Unit,
    viewModel: BookReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(title, bookUrl, initialChapterIndex) {
        viewModel.loadBook(title, bookUrl, initialChapterIndex)
    }

    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        is BookReaderState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        is BookReaderState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = current.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onNavigateBack) {
                        Text("Go Back")
                    }
                }
            }
        }
        is BookReaderState.Success -> {
            BookReaderContent(
                state = current,
                onEvent = viewModel::onEvent,
                onNavigateBack = onNavigateBack,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookReaderContent(
    state: BookReaderState.Success,
    onEvent: (BookReaderEvent) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.currentChapter?.let { ch ->
                            Text(
                                text = ch.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(BookReaderEvent.ToggleToc(true)) }) {
                        Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Table of Contents")
                    }
                    IconButton(onClick = { onEvent(BookReaderEvent.ToggleSettings(!state.showSettings)) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (state.isPaginated) {
                PaginatedChapterReader(
                    state = state,
                    onChapterChanged = { onEvent(BookReaderEvent.SelectChapter(it)) },
                )
            } else {
                ScrollChapterReader(
                    state = state,
                    onNext = { onEvent(BookReaderEvent.NextChapter) },
                    onPrevious = { onEvent(BookReaderEvent.PreviousChapter) },
                    onScrollChanged = { onEvent(BookReaderEvent.SaveScrollOffset(it)) },
                )
            }

            if (state.showSettings) {
                BookReaderSettingsPanel(
                    state = state,
                    onEvent = onEvent,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            if (state.showToc) {
                ModalBottomSheet(
                    onDismissRequest = { onEvent(BookReaderEvent.ToggleToc(false)) },
                    sheetState = sheetState,
                ) {
                    TableOfContentsSheet(
                        state = state,
                        onSelectChapter = { onEvent(BookReaderEvent.SelectChapter(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterBodyContent(
    chapter: EpubChapter,
    state: BookReaderState.Success,
) {
    if (chapter.paragraphs.isNotEmpty()) {
        for (paragraph in chapter.paragraphs) {
            if (paragraph == "* * *") {
                Text(
                    text = "* * *",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                )
            } else {
                Text(
                    text = paragraph,
                    fontSize = state.fontSize.sp,
                    fontFamily = if (state.isSerif) FontFamily.Serif else FontFamily.SansSerif,
                    lineHeight = (state.fontSize * 1.6f).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    } else {
        Text(
            text = chapter.bodyText,
            fontSize = state.fontSize.sp,
            fontFamily = if (state.isSerif) FontFamily.Serif else FontFamily.SansSerif,
            lineHeight = (state.fontSize * 1.6f).sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ScrollChapterReader(
    state: BookReaderState.Success,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onScrollChanged: (Int) -> Unit = {},
) {
    val scrollState = rememberScrollState(initial = state.initialScrollOffset)

    LaunchedEffect(state.currentChapterIndex) {
        scrollState.scrollTo(state.initialScrollOffset)
    }

    LaunchedEffect(scrollState.value) {
        onScrollChanged(scrollState.value)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
    ) {
        val chapter = state.currentChapter
        if (chapter != null) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(16.dp))
            ChapterBodyContent(chapter = chapter, state = state)
            Spacer(modifier = Modifier.height(32.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    enabled = state.hasPrevious,
                ) {
                    Text("Previous")
                }
                OutlinedButton(
                    onClick = onNext,
                    enabled = state.hasNext,
                ) {
                    Text("Next")
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun PaginatedChapterReader(
    state: BookReaderState.Success,
    onChapterChanged: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentChapterIndex,
        pageCount = { state.chapters.size },
    )

    LaunchedEffect(state.currentChapterIndex) {
        if (pagerState.currentPage != state.currentChapterIndex) {
            pagerState.scrollToPage(state.currentChapterIndex)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page != state.currentChapterIndex) {
                onChapterChanged(page)
            }
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        val chapter = state.chapters.getOrNull(page)
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
        ) {
            if (chapter != null) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                ChapterBodyContent(chapter = chapter, state = state)
            }
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun TableOfContentsSheet(
    state: BookReaderState.Success,
    onSelectChapter: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Text(
            text = "Table of Contents",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        HorizontalDivider()
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
        ) {
            itemsIndexed(state.chapters) { index, chapter ->
                val isSelected = index == state.currentChapterIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectChapter(index) }
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BookReaderSettingsPanel(
    state: BookReaderState.Success,
    onEvent: (BookReaderEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Font Size", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = state.fontSize,
                    onValueChange = { onEvent(BookReaderEvent.SetFontSize(it)) },
                    valueRange = 14f..32f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                )
                Text("${state.fontSize.toInt()}sp", style = MaterialTheme.typography.bodyMedium)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Typeface", style = MaterialTheme.typography.bodyMedium)
                Row {
                    Text(
                        text = "Serif",
                        modifier = Modifier
                            .clickable { onEvent(BookReaderEvent.SetSerif(true)) }
                            .background(
                                if (state.isSerif) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (state.isSerif) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sans-Serif",
                        modifier = Modifier
                            .clickable { onEvent(BookReaderEvent.SetSerif(false)) }
                            .background(
                                if (!state.isSerif) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (!state.isSerif) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Paging Mode", style = MaterialTheme.typography.bodyMedium)
                Row {
                    Text(
                        text = "Scroll",
                        modifier = Modifier
                            .clickable { onEvent(BookReaderEvent.SetPaginated(false)) }
                            .background(
                                if (!state.isPaginated) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (!state.isPaginated) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Paged",
                        modifier = Modifier
                            .clickable { onEvent(BookReaderEvent.SetPaginated(true)) }
                            .background(
                                if (state.isPaginated) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (state.isPaginated) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}
