package echo.music.iad1tya.ui.screens.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.pages.MoodAndGenres
import com.music.innertube.utils.YouTubeUrlParser
import echo.music.iad1tya.LocalDatabase
import echo.music.iad1tya.LocalIsPlayerExpanded
import echo.music.iad1tya.LocalPlayerAwareWindowInsets
import echo.music.iad1tya.LocalPlayerConnection
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.PauseSearchHistoryKey
import echo.music.iad1tya.constants.SearchSource
import echo.music.iad1tya.constants.SearchSourceKey
import echo.music.iad1tya.db.entities.SearchHistory
import echo.music.iad1tya.playback.queues.YouTubeQueue
import echo.music.iad1tya.utils.rememberEnumPreference
import echo.music.iad1tya.utils.rememberPreference
import echo.music.iad1tya.viewmodels.HomeViewModel
import echo.music.iad1tya.viewmodels.MoodAndGenresViewModel
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
  navController: NavController,
  pureBlack: Boolean,
  homeViewModel: HomeViewModel = hiltViewModel(),
  moodAndGenresViewModel: MoodAndGenresViewModel = hiltViewModel(),
) {
  val database = LocalDatabase.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  val isPlayerExpanded = LocalIsPlayerExpanded.current
  val playerConnection = LocalPlayerConnection.current
  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

  val moodAndGenresList by moodAndGenresViewModel.moodAndGenres.collectAsState()

  var searchSource by rememberEnumPreference(SearchSourceKey, SearchSource.ONLINE)
  var query by
    rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
  val pauseSearchHistory by rememberPreference(PauseSearchHistoryKey, defaultValue = false)

  var searchActive by rememberSaveable { mutableStateOf(false) }
  var showSearchContent by remember { mutableStateOf(false) }

  val speechRecognizerLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        val spokenText =
          result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spokenText.isNullOrBlank()) {
          query = TextFieldValue(spokenText)
          if (spokenText.isNotEmpty()) {
            focusManager.clearFocus()
            navController.navigate("search/${URLEncoder.encode(spokenText, "UTF-8")}")
            if (!pauseSearchHistory) {
              coroutineScope.launch(Dispatchers.IO) {
                database.query { insert(SearchHistory(query = spokenText)) }
              }
            }
          }
          searchActive = false
        }
      }
    }

  LaunchedEffect(searchActive) {
    if (searchActive) {
      delay(80)
      showSearchContent = true
    } else {
      showSearchContent = false
    }
  }

  BackHandler(enabled = searchActive) {
    searchActive = false
    query = TextFieldValue("")
    focusManager.clearFocus()
    keyboardController?.hide()
  }

  val onSearch: (String) -> Unit = remember {
    { searchQuery ->
      if (searchQuery.isNotEmpty()) {
        searchActive = false
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        when (val parsedUrl = YouTubeUrlParser.parse(searchQuery)) {
          is YouTubeUrlParser.ParsedUrl.Video -> {
            playerConnection?.playQueue(
              YouTubeQueue(
                WatchEndpoint(videoId = parsedUrl.id),
              ),
            )
          }
          is YouTubeUrlParser.ParsedUrl.Artist -> {
            navController.navigate("artist/${parsedUrl.id}")
          }
          null -> {
            navController.navigate("search/${URLEncoder.encode(searchQuery, "UTF-8")}")
          }
        }

        if (!pauseSearchHistory) {
          coroutineScope.launch(Dispatchers.IO) {
            database.query { insert(SearchHistory(query = searchQuery)) }
          }
        }
      }
    }
  }

  val onCategoryClick: (MoodAndGenres.Item) -> Unit = { item ->
    navController.navigate(
      "youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}"
    )
  }

  val bottomPadding =
    LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()

  Scaffold(
    topBar = {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
      ) {
        // Top Header: Title "Search"
        if (!searchActive) {
          Row(
            modifier =
              Modifier.fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = stringResource(R.string.search),
              style =
                MaterialTheme.typography.titleLarge.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 24.sp
                ),
            )
          }
        }

        // Material You Stadium-Pill SearchBar
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .padding(horizontal = 16.dp)
              .padding(top = if (searchActive) 10.dp else 0.dp, bottom = 12.dp)
              .height(56.dp)
              .clip(CircleShape)
              .background(
                if (pureBlack) Color.White.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerHigh
              )
              .clickable {
                if (!searchActive) {
                  searchActive = true
                  focusRequester.requestFocus()
                }
              }
              .padding(horizontal = 12.dp),
          contentAlignment = Alignment.CenterStart,
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            IconButton(
              onClick = {
                if (searchActive) {
                  searchActive = false
                  query = TextFieldValue("")
                  focusManager.clearFocus()
                  keyboardController?.hide()
                } else {
                  searchActive = true
                  focusRequester.requestFocus()
                }
              },
              modifier = Modifier.size(36.dp),
            ) {
              Icon(
                painter =
                  painterResource(
                    if (searchActive) R.drawable.arrow_back else R.drawable.search
                  ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
              )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(modifier = Modifier.weight(1f)) {
              if (query.text.isEmpty()) {
                Text(
                  text =
                    if (searchSource == SearchSource.ONLINE) "Artists, Songs, Lyrics & More"
                    else stringResource(R.string.search_library),
                  style =
                    TextStyle(
                      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                      fontSize = 16.sp,
                    ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
              BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier =
                  Modifier.fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { if (it.isFocused) searchActive = true },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions =
                  KeyboardActions(
                    onSearch = {
                      focusManager.clearFocus(force = true)
                      keyboardController?.hide()
                      searchActive = false
                      onSearch(query.text)
                    }
                  ),
                textStyle =
                  TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                  ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
              )
            }

            // Right-side actions: Clear / Mic / Source Toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (query.text.isNotEmpty()) {
                IconButton(
                  onClick = { query = TextFieldValue("") },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                  )
                }
              } else {
                IconButton(
                  onClick = {
                    try {
                      val intent =
                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                          putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                          )
                          putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search...")
                        }
                      speechRecognizerLauncher.launch(intent)
                    } catch (e: Exception) {
                      navController.navigate("recognition")
                    }
                  },
                  modifier = Modifier.size(36.dp),
                ) {
                  Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                  )
                }
              }

              IconButton(
                onClick = {
                  searchSource =
                    if (searchSource == SearchSource.ONLINE) SearchSource.LOCAL
                    else SearchSource.ONLINE
                },
                modifier = Modifier.size(36.dp),
              ) {
                Icon(
                  painter =
                    painterResource(
                      when (searchSource) {
                        SearchSource.LOCAL -> R.drawable.library_music
                        SearchSource.ONLINE -> R.drawable.globe_search
                      }
                    ),
                  contentDescription = "Search Source",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(21.dp),
                )
              }
            }
          }
        }
      }
    },
    containerColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Box(
      modifier =
        Modifier.fillMaxSize()
          .padding(top = paddingValues.calculateTopPadding())
    ) {
      if (searchActive && showSearchContent) {
        when (searchSource) {
          SearchSource.LOCAL ->
            LocalSearchScreen(
              query = query.text,
              navController = navController,
              onDismiss = {
                searchActive = false
                focusManager.clearFocus()
                keyboardController?.hide()
              },
              pureBlack = pureBlack,
            )
          SearchSource.ONLINE ->
            OnlineSearchScreen(
              query = query.text,
              onQueryChange = { query = it },
              navController = navController,
              onSearch = {
                searchActive = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
                onSearch(it)
              },
              onDismiss = {
                searchActive = false
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
              },
              pureBlack = pureBlack,
            )
        }
      } else if (!searchActive) {
        // Live YouTube Music mood/genre discovery; each card opens its own browse endpoint.
        val categories = moodAndGenresList.orEmpty().flatMap { it.items }
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          contentPadding =
            PaddingValues(
              start = 16.dp,
              end = 16.dp,
              top = 4.dp,
              bottom = bottomPadding + 24.dp,
            ),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          items(
            items = categories,
            key = { "${it.endpoint.browseId}:${it.title}" },
          ) { category ->
            SearchGenreCard(
              category = category,
              onClick = { onCategoryClick(category) },
            )
          }
        }
      }
    }
  }

  DisposableEffect(lifecycleOwner, isPlayerExpanded) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> {
          if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
          }
        }
        Lifecycle.Event.ON_PAUSE -> {
          focusManager.clearFocus()
          keyboardController?.hide()
        }
        else -> {}
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)

    if (isPlayerExpanded) {
      keyboardController?.hide()
      focusManager.clearFocus()
    }

    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
}

@Composable
private fun SearchGenreCard(
  category: MoodAndGenres.Item,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val accent = Color(category.stripeColor)
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .height(104.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .clickable(onClick = onClick),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(accent.copy(alpha = 0.16f)),
    )
    Box(
      modifier =
        Modifier.width(5.dp)
          .fillMaxSize()
          .background(accent),
    )
    Text(
      text = category.title,
      color = MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 12.dp, bottom = 14.dp),
    )
  }
}
