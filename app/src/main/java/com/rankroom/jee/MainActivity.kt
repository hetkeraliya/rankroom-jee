package com.rankroom.jee

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.collections.toList

private data class Question(
    val id: String,
    val exam: String,
    val year: Int,
    val subject: String,
    val paper: String,
    val number: Int,
    val text: String,
    val choices: List<String>,
    val answer: Int,
    val answerLetter: String
)

private fun loadArchive(context: Context): List<Question> = runCatching {
    val root = context.assets.open("question-bank.json").bufferedReader().use { JSONObject(it.readText()) }
    val documents = root.getJSONArray("documents")
    buildList {
        for (documentIndex in 0 until documents.length()) {
            val questions = documents.getJSONObject(documentIndex).getJSONArray("questions")
            for (questionIndex in 0 until questions.length()) {
                val question = questions.getJSONObject(questionIndex)
                val choices = if (question.has("choices")) {
                    val arr = question.getJSONArray("choices")
                    (0 until arr.length()).map { arr.getString(it) }
                } else {
                    listOf("", "", "", "")
                }
                add(
                    Question(
                        id = question.getString("id"),
                        exam = question.getString("exam"),
                        year = question.getInt("year"),
                        subject = question.getString("subject"),
                        paper = question.getString("paper"),
                        number = question.getInt("number"),
                        text = question.getString("text"),
                        choices = choices,
                        answer = if (question.has("answer")) question.getInt("answer") else -1,
                        answerLetter = if (question.has("answer_letter")) question.getString("answer_letter") else ""
                    )
                )
            }
        }
    }
}.getOrDefault(emptyList())

// These are illustrative practice examples, not a verified PYQ archive.
private val bank = listOf(
    Question("p1", "JEE Main", 2023, "Physics", "AIEEE 2012 (Online) 19th May Question Paper with Answer Key", 1,
        "A 2 kg block rests on a rough horizontal surface. The coefficient of static friction is 0.4. Find the force needed to just start moving it. Take g = 10 m/s².",
        listOf("4 N", "8 N", "20 N", "5 N"), 1, "A"),
    Question("p2", "JEE Advanced", 2022, "Physics", "IIT-JEE 2010 Paper 2 Offline Question Paper with Answer Key", 2,
        "Two equal positive point charges are fixed at x = −a and x = +a. The electric field at the origin is:",
        listOf("Zero", "Along +x", "Along −x", "Infinite"), 0, "A"),
    Question("c1", "JEE Main", 2024, "Chemistry", "AIEEE 2012 (Online) 7th May Question Paper with Answer Key", 3,
        "According to VSEPR theory, the molecular shape of NH₃ is:",
        listOf("Trigonal planar", "Trigonal pyramidal", "Tetrahedral", "Linear"), 1, "B"),
    Question("c2", "JEE Advanced", 2021, "Chemistry", "IIT-JEE 2010 Paper 1 Offline Question Paper with Answer Key", 4,
        "At 25 °C, the pH of a 1.0 × 10⁻³ M strong monoprotic acid is approximately:",
        listOf("2", "3", "11", "1"), 1, "B"),
    Question("m1", "JEE Main", 2023, "Mathematics", "AIEEE 2012 (Online) 19th May Question Paper with Answer Key", 5,
        "Evaluate limₓ→₀ (sin 3x)/(2x).",
        listOf("3/2", "2/3", "3", "1/2"), 0, "A"),
    Question("m2", "JEE Advanced", 2020, "Mathematics", "IIT-JEE 2010 Paper 2 Offline Question Paper with Answer Key", 6,
        "If A = [[1, 2], [3, 4]], what is det(A)?",
        listOf("2", "−2", "10", "−10"), 1, "B")
)

private val examYears = (2002..2026).toList().reversed()
private val pageItems = listOf(Page.Home, Page.Questions, Page.Papers, Page.Mock, Page.Progress, Page.CustomTest)
private val ink = Color(0xFF172A43)
private val cobalt = Color(0xFF315F9B)
private val amber = Color(0xFFB47A18)
private val cloud = Color(0xFFF3F6FA)
private val darkInk = Color(0xFFE7EDF5)

private enum class Page { Home, Questions, Papers, Mock, Progress, CustomTest }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { RankroomApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankroomApp() {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val archive = remember(context) { loadArchive(context) }
    val prefs = remember { context.getSharedPreferences("rankroom", 0) }
    val attempts = remember {
        mutableStateMapOf<String, Boolean>().apply {
            prefs.all.forEach { (key, value) ->
                if (key.startsWith("q_") && value is Boolean) put(key.removePrefix("q_"), value)
            }
        }
    }
    var page by remember { mutableStateOf(Page.Home) }
    var search by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("All") }
    var exam by remember { mutableStateOf("All") }
    var year by remember { mutableIntStateOf(0) }
    var mockIndex by remember { mutableIntStateOf(0) }
    var mockActive by remember { mutableStateOf(false) }
    var mockSubmitted by remember { mutableStateOf(false) }
    var mockSeconds by remember { mutableIntStateOf(1800) }
    val mockChoices = remember { mutableStateMapOf<String, Int>() }
    var mockCount by remember { mutableIntStateOf(prefs.getInt("mocks", 0)) }
    // Custom test state
    var customSubject by remember { mutableStateOf("All") }
    var customExam by remember { mutableStateOf("All") }
    var customYear by remember { mutableIntStateOf(0) }
    var customChapter by remember { mutableStateOf("All") }
    var customQuestionCount by remember { mutableIntStateOf(10) }
    var customTestActive by remember { mutableStateOf(false) }
    var customTestSubmitted by remember { mutableStateOf(false) }
    var customTestIndex by remember { mutableIntStateOf(0) }
    var customTestSeconds by remember { mutableIntStateOf(1800) }
    val customTestChoices = remember { mutableStateMapOf<String, Int>() }
    val customTestQuestions = remember(customSubject, customExam, customYear, customChapter, customQuestionCount) {
        generateCustomTest(archive, customSubject, customExam, customYear, customChapter, customQuestionCount)
    }
    val isDark = isSystemInDarkTheme()
    val scheme = if (isDark) {
        darkColorScheme(
            primary = Color(0xFFAAC8F2), onPrimary = Color(0xFF10243D),
            secondary = Color(0xFFF0C36A), onSecondary = Color(0xFF342300),
            background = Color(0xFF101820), surface = Color(0xFF17232F),
            surfaceVariant = Color(0xFF253647), onSurface = darkInk,
            onSurfaceVariant = Color(0xFFB3C0CF), outline = Color(0xFF435568)
        )
    } else {
        lightColorScheme(
            primary = cobalt, onPrimary = Color.White,
            secondary = amber, onSecondary = Color.White,
            background = cloud, surface = Color.White,
            surfaceVariant = Color(0xFFE9EEF5), onSurface = ink,
            onSurfaceVariant = Color(0xFF5B6B7E), outline = Color(0xFFD7E0E9)
        )
    }

    LaunchedEffect(mockActive, mockSubmitted) {
        while (mockActive && !mockSubmitted && mockSeconds > 0) {
            delay(1000)
            mockSeconds--
        }
        if (mockSeconds == 0) mockSubmitted = true
    }

    val attempted = attempts.size
    val correct = attempts.values.count { it }
    val accuracy = if (attempted == 0) 0 else correct * 100 / attempted
    val filteredArchive = remember(archive, subject, exam, year, search) {
        archive.filter { q ->
            (subject == "All" || q.subject == subject) &&
                (exam == "All" || q.exam == exam) &&
                (year == 0 || q.year == year) &&
                (search.isBlank() || "${q.text} ${q.paper} ${q.year} ${q.subject}".contains(search, true))
        }
    }

    MaterialTheme(colorScheme = scheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("rankroom", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
                            Text(page.title(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    pageItems.forEach { destination ->
                        val label = destination.title()
                        NavigationBarItem(
                            selected = page == destination,
                            onClick = { page = destination },
                            icon = { Icon(destination.icon(), contentDescription = label) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            alwaysShowLabel = false
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                when (page) {
                    Page.Home -> {
                        item { HomeHeader(attempted) }
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Text("A little practice, every day.", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Find a question by exam, year, and subject.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.86f))
                                    Button(
                                        onClick = { page = Page.Questions },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.primary)
                                    ) { Text("Browse questions", fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                        item { Text("Your progress", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Metric("Solved", "$attempted")
                                Metric("Accuracy", if (attempted == 0) "—" else "$accuracy%")
                                Metric("Mocks", "$mockCount")
                            }
                        }
                        item { Text("Practice by subject", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                        items(listOf("Physics", "Chemistry", "Mathematics")) { s ->
                            val total = archive.count { it.subject == s }
                            val done = archive.count { it.subject == s && attempts.containsKey(it.id) }
                            val correct = archive.count { it.subject == s && attempts[it.id] == true }
                            val pct = if (done == 0) 0 else correct * 100 / done
                            SubjectRow(s, done, total, pct) {
                                subject = s
                                page = Page.Questions
                            }
                        }
                        item { ArchiveNotice(onOpen = { page = Page.Papers }, onSource = { url -> openUrl(uriHandler, url) }) }
                        item { Text("Text was extracted from 146 PDFs. Diagrams, images, and some equation formatting may be missing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Page.Questions -> {
                        item { ScreenHeading("Find your next question", "Browse extracted questions by exam, year, and subject.") }
                        item {
                            OutlinedTextField(
                                value = search,
                                onValueChange = { search = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("Search topic or question") },
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                        item { Text("Exam", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                        item { ChoiceChips(listOf("All", "JEE Main", "JEE Advanced"), exam) { exam = it as String } }
                        item { Text("Year", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                        item { ChoiceChips(listOf(0) + examYears, year, yearLabel = true) { year = it as Int } }
                        item { Text("Subject", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                        item { ChoiceChips(listOf("All", "Physics", "Chemistry", "Mathematics"), subject) { subject = it as String } }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Available in this build", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${filteredArchive.size} questions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (filteredArchive.isEmpty()) {
                            item { EmptyQuestions(onOpenPapers = { page = Page.Papers }) }
                        } else {
                            items(filteredArchive, key = { it.id }) { q ->
                                ArchiveQuestionCard(q) { question, choice ->
                                    val isCorrect = choice == question.answer
                                    attempts[question.id] = isCorrect
                                    prefs.edit().putBoolean("q_${question.id}", isCorrect).apply()
                                }
                            }
                        }
                    }
                    Page.Papers -> {
                        item { ScreenHeading("Official paper sources", "Open the exam authorities’ original paper archives.") }
                        item {
                            Text("Text archive coverage: 2010–2026", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        item {
                            SourceRow(
                                title = "JEE Advanced",
                                detail = "Official archive · papers listed for 2017–2025",
                                action = "Open archive",
                                onClick = { openUrl(uriHandler, "https://jeeadv.ac.in/archive.html") }
                            )
                        }
                        item {
                            SourceRow(
                                title = "JEE Main",
                                detail = "NTA’s official examination portal",
                                action = "Open NTA",
                                onClick = { openUrl(uriHandler, "https://jeemain.nta.nic.in/") }
                            )
                        }
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Archive status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "The app includes 11,841 text entries extracted from 146 PDFs covering 2010–2026. The text has not been individually verified; diagrams, scanned content, and some equations may be missing. The archive does not cover 2002–2009, and answer keys are not included in these question cards.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        item { Text("JEE Advanced archive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        items((2017..2025).toList().reversed()) { y ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("JEE Advanced $y", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text("Paper 1 and Paper 2", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { openUrl(uriHandler, "https://jeeadv.ac.in/archive.html") }) {
                                    Text("View")
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        item { Text("NTA’s archive is reached through the official portal; access and year coverage may depend on the current NTA page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Page.Mock -> {
                        item { ScreenHeading("Timed practice", "A short demo mock using the bundled examples.") }
                        item {
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("30 minutes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                                    Text("Six illustrative questions · +4 / −1 demo scoring", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (!mockActive || mockSubmitted) {
                            item {
                                Button(onClick = {
                                    mockActive = true
                                    mockSubmitted = false
                                    mockIndex = 0
                                    mockSeconds = 1800
                                    mockChoices.clear()
                                }, modifier = Modifier.fillMaxWidth()) { Text(if (mockSubmitted) "Start another demo" else "Start demo mock") }
                            }
                        }
                        if (mockActive && !mockSubmitted) {
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Question ${mockIndex + 1} of ${bank.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${mockSeconds / 60}:${(mockSeconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            item { QuestionCard(bank[mockIndex], null, showSolution = false, selectedChoice = mockChoices[bank[mockIndex].id], onAnswer = { mockChoices[bank[mockIndex].id] = it }) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { mockIndex = (mockIndex - 1).coerceAtLeast(0) }, modifier = Modifier.weight(1f)) { Text("Previous") }
                                    Button(onClick = { mockIndex = (mockIndex + 1).coerceAtMost(bank.lastIndex) }, modifier = Modifier.weight(1f)) { Text("Next") }
                                }
                            }
                            item {
                                Button(onClick = {
                                    bank.forEach { q ->
                                        mockChoices[q.id]?.let { choice ->
                                            val ok = choice == q.answer
                                            attempts[q.id] = ok
                                            prefs.edit().putBoolean("q_${q.id}", ok).apply()
                                        }
                                    }
                                    mockCount++
                                    prefs.edit().putInt("mocks", mockCount).apply()
                                    mockSubmitted = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("Submit mock") }
                            }
                        }
                        if (mockSubmitted) {
                            var score = 0
                            for (q in bank) score += mockChoices[q.id]?.let { if (it == q.answer) 4 else -1 } ?: 0
                            item { ResultSummary(score, mockChoices.size, bank.size) }
                        }
                    }
                    Page.CustomTest -> {
                        if (!customTestActive || customTestSubmitted) {
                            item { ScreenHeading("Custom test", "Build a test tailored to your needs. Pick subject, exam, year, chapter, and question count.") }
                            item { Text("Subject", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                            item { ChoiceChips(listOf("All", "Physics", "Chemistry", "Mathematics"), customSubject) { customSubject = it as String } }
                            item { Text("Exam", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                            item { ChoiceChips(listOf("All", "JEE Main", "JEE Advanced"), customExam) { customExam = it as String } }
                            item { Text("Year", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                            item { ChoiceChips(listOf(0) + examYears, customYear, yearLabel = true) { customYear = it as Int } }
                            item { Text("Chapter/Topic", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                            item { ChoiceChips(getChaptersForFilters(archive, customSubject, customExam, customYear), customChapter) { customChapter = it as String } }
                            item { Text("Number of questions", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    listOf(5, 10, 15, 20, 25, 30).forEach { n ->
                                        FilterChip(
                                            selected = customQuestionCount == n,
                                            onClick = { customQuestionCount = n },
                                            label = { Text("$n") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${customTestQuestions.size} questions available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            item {
                                Button(
                                    onClick = {
                                        customTestActive = true
                                        customTestSubmitted = false
                                        customTestIndex = 0
                                        customTestSeconds = customQuestionCount * 60 // 1 min per question
                                        customTestChoices.clear()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = customTestQuestions.isNotEmpty()
                                ) { Text(if (customTestSubmitted) "Start another test" else "Start custom test (${customQuestionCount} min)") }
                            }
                            if (customTestQuestions.isEmpty()) {
                                item { EmptyQuestions(onOpenPapers = { page = Page.Papers }) }
                            }
                        }
                        if (customTestActive && !customTestSubmitted) {
                            val currentQuestion = customTestQuestions[customTestIndex]
                            item {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Question ${customTestIndex + 1} of ${customTestQuestions.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${customTestSeconds / 60}:${(customTestSeconds % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            item { ArchiveQuestionCard(currentQuestion) { question, choice ->
                                customTestChoices[question.id] = choice
                            } }
                            item {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { customTestIndex = (customTestIndex - 1).coerceAtLeast(0) }, modifier = Modifier.weight(1f)) { Text("Previous") }
                                    Button(onClick = { customTestIndex = (customTestIndex + 1).coerceAtMost(customTestQuestions.lastIndex) }, modifier = Modifier.weight(1f)) { Text("Next") }
                                }
                            }
                            item {
                                Button(onClick = {
                                    var score = 0
                                    customTestQuestions.forEach { q ->
                                        customTestChoices[q.id]?.let { choice ->
                                            val ok = choice == q.answer
                                            attempts[q.id] = ok
                                            prefs.edit().putBoolean("q_${q.id}", ok).apply()
                                            if (ok) score += 4 else score -= 1
                                        }
                                    }
                                    mockCount++
                                    prefs.edit().putInt("mocks", mockCount).apply()
                                    customTestSubmitted = true
                                }, modifier = Modifier.fillMaxWidth()) { Text("Submit test") }
                            }
                        }
                        if (customTestSubmitted) {
                            var score = 0
                            var correctCount = 0
                            customTestQuestions.forEach { q ->
                                customTestChoices[q.id]?.let { choice ->
                                    if (choice == q.answer) { correctCount++; score += 4 } else { score -= 1 }
                                }
                            }
                            item { ResultSummary(score, correctCount, customTestQuestions.size) }
                        }
                    }
                    Page.Progress -> {
                        item { ScreenHeading("Your progress", "Saved on this device.") }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                Metric("Solved", "$attempted")
                                Metric("Accuracy", if (attempted == 0) "—" else "$accuracy%")
                                Metric("Mocks", "$mockCount")
                            }
                        }
                        item { Text("Subject readiness", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
                        items(listOf("Physics", "Chemistry", "Mathematics")) { s ->
                            val qs = bank.filter { it.subject == s }
                            val done = qs.count { attempts.containsKey(it.id) }
                            val pct = if (qs.isEmpty()) 0 else done * 100 / qs.size
                            SubjectRow(s, done, qs.size, pct) {}
                        }
                        item {
                            TextButton(onClick = { prefs.edit().clear().apply(); attempts.clear(); mockCount = 0 }) {
                                Text("Reset saved progress", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Page.title(): String = when (this) {
    Page.Home -> "Study desk"
    Page.Questions -> "Question bank"
    Page.Papers -> "Official papers"
    Page.Mock -> "Timed practice"
    Page.Progress -> "Progress"
    Page.CustomTest -> "Custom test"
}

@Composable
private fun Page.icon() = when (this) {
    Page.Home -> Icons.Default.Home
    Page.Questions -> Icons.Default.MenuBook
    Page.Papers -> Icons.Default.Search
    Page.Mock -> Icons.Default.Assessment
    Page.Progress -> Icons.Default.Assessment
    Page.CustomTest -> Icons.Default.Edit
}

@Composable
private fun HomeHeader(attempted: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Good to see you.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp)
        Text(
            if (attempted == 0) "Start with one question." else "$attempted questions attempted so far.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowScope.Metric(label: String, value: String) {
    Card(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun SubjectRow(subject: String, done: Int, total: Int, percent: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (done == 0) "Start" else "$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { if (total == 0) 0f else percent / 100f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
            Text(if (done == 0) "$total questions available" else "$done of $total attempted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ArchiveNotice(onOpen: () -> Unit, onSource: (String) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Question archive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("The question bank contains extracted text from the PDFs bundled with this build. Figures, scanned content, and some equation formatting may be missing; use official papers to verify anything unclear.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("See sources") }
                TextButton(onClick = { onSource("https://jeeadv.ac.in/archive.html") }) {
                    Text("Advanced archive")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 15.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onClick) {
                Text(action)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun EmptyQuestions(onOpenPapers: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No extracted questions match these filters.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Try a different exam, year, subject, or search term.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenPapers) { Text("Open official sources") }
    }
}

@Composable
private fun ArchiveQuestionCard(question: Question, onAnswer: ((Question, Int) -> Unit)? = null) {
    var selectedChoice by remember(question.id) { mutableIntStateOf(-1) }
    var answered by remember(question.id) { mutableStateOf(false) }
    val isCorrect = question.answer >= 0 && selectedChoice == question.answer
    val hasOptions = question.choices.any { it.isNotBlank() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${question.exam} · ${question.year} · Q${question.number}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Text("${question.subject} · ${question.paper}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.text, style = MaterialTheme.typography.bodyLarge, lineHeight = 25.sp)

            if (hasOptions) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    question.choices.forEachIndexed { index, option ->
                        if (option.isNotBlank()) {
                            val optionLabel = "${'A' + index}.  $option"
                            if (answered) {
                                val correctChoice = index == question.answer
                                val wrongChoice = index == selectedChoice && !isCorrect
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = when {
                                        correctChoice -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                                        wrongChoice -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                ) {
                                    Text(
                                        (if (correctChoice) "✓  " else if (wrongChoice) "×  " else "") + optionLabel,
                                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (correctChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        selectedChoice = index
                                        answered = true
                                        onAnswer?.invoke(question, index)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                                ) {
                                    Text((if (selectedChoice == index) "●  " else "○  ") + optionLabel, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Options not available in extracted text", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (answered && question.answer >= 0) {
                Text(if (isCorrect) "Correct!" else "Answer: ${question.answerLetter}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ChoiceChips(options: List<Any>, selected: Any, yearLabel: Boolean = false, onSelect: (Any) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val label = when {
                yearLabel && option == 0 -> "All years"
                else -> option.toString()
            }
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun ResultSummary(score: Int, answered: Int, total: Int) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Mock complete", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Score  $score", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("$answered of $total answered · example questions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuestionCard(
    question: Question,
    previousCorrect: Boolean?,
    showSolution: Boolean = true,
    selectedChoice: Int? = null,
    onAnswer: (Int) -> Unit
) {
    var choice by remember(question.id) { mutableIntStateOf(-1) }
    var answered by remember(question.id) { mutableStateOf(previousCorrect != null) }
    val selected = selectedChoice ?: if (answered) question.answer.takeIf { previousCorrect == true } ?: choice else choice
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${question.exam} · ${question.year}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("EXAMPLE", Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("${question.subject}  ·  ${question.paper}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, lineHeight = 25.sp)
            question.choices.forEachIndexed { index, option ->
                val optionLabel = "${'A' + index}.  $option"
                if (answered && showSolution) {
                    val correctChoice = index == question.answer
                    val wrongChoice = index == selected && previousCorrect == false
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            correctChoice -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            wrongChoice -> MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Text(
                            (if (correctChoice) "✓  " else if (wrongChoice) "×  " else "") + optionLabel,
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (correctChoice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = { choice = index; if (showSolution) { answered = true; onAnswer(index) } else onAnswer(index) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Text((if (selected == index) "●  " else "○  ") + optionLabel, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (answered && showSolution) {
                Text(if (previousCorrect == true || (previousCorrect == null && choice == question.answer)) "Correct answer" else "Solution", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text("Answer: ${question.answerLetter}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 23.sp)
            }
        }
    }
}

private fun generateCustomTest(
    archive: List<Question>,
    subject: String,
    exam: String,
    year: Int,
    chapter: String,
    count: Int
): List<Question> {
    val filtered = archive.filter { q ->
        (subject == "All" || q.subject == subject) &&
            (exam == "All" || q.exam == exam) &&
            (year == 0 || q.year == year) &&
            (chapter == "All" || q.paper.contains(chapter)) &&
            q.answer >= 0 && q.choices.any { it.isNotBlank() }
    }
    return filtered.shuffled().take(count)
}

private fun getChaptersForFilters(
    archive: List<Question>,
    subject: String,
    exam: String,
    year: Int
): List<String> {
    val filtered = archive.filter { q ->
        (subject == "All" || q.subject == subject) &&
            (exam == "All" || q.exam == exam) &&
            (year == 0 || q.year == year)
    }
    // Extract chapter-like identifiers from paper names
    val chapters = filtered
        .map { it.paper }
        .distinct()
        .sorted()
    return listOf("All") + chapters.take(20)
}

private fun openUrl(uriHandler: androidx.compose.ui.platform.UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }
}
