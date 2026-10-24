package com.rankroom.jee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class Question(
    val id: String, val exam: String, val year: Int, val subject: String,
    val chapter: String, val topic: String, val prompt: String,
    val choices: List<String>, val answer: Int, val explanation: String
)

private val bank = listOf(
    Question("p1", "JEE Main", 2023, "Physics", "Laws of Motion", "Friction",
        "A 2 kg block rests on a rough horizontal surface. The coefficient of static friction is 0.4. Find the force needed to just start moving it. Take g = 10 m/s².",
        listOf("4 N", "8 N", "20 N", "5 N"), 1,
        "At the limiting condition, friction is μN. On a horizontal surface N = mg = 20 N, so fₘₐₓ = 0.4 × 20 = 8 N."),
    Question("p2", "JEE Advanced", 2022, "Physics", "Electrostatics", "Electric field",
        "Two equal positive point charges are fixed at x = −a and x = +a. The electric field at the origin is:",
        listOf("Zero", "Along +x", "Along −x", "Infinite"), 0,
        "The two fields have equal magnitude kq/a² and opposite directions at the origin, so they cancel."),
    Question("c1", "JEE Main", 2024, "Chemistry", "Chemical Bonding", "Molecular shape",
        "According to VSEPR theory, the molecular shape of NH₃ is:",
        listOf("Trigonal planar", "Trigonal pyramidal", "Tetrahedral", "Linear"), 1,
        "Nitrogen has three bonding pairs and one lone pair. The electron-pair geometry is tetrahedral; the molecular shape is trigonal pyramidal."),
    Question("c2", "JEE Advanced", 2021, "Chemistry", "Equilibrium", "pH",
        "At 25 °C, the pH of a 1.0 × 10⁻³ M strong monoprotic acid is approximately:",
        listOf("2", "3", "11", "1"), 1,
        "The acid dissociates completely, so [H⁺] ≈ 10⁻³ M. Therefore pH = −log₁₀(10⁻³) = 3."),
    Question("m1", "JEE Main", 2023, "Mathematics", "Limits", "Standard limits",
        "Evaluate limₓ→₀ (sin 3x)/(2x).",
        listOf("3/2", "2/3", "3", "1/2"), 0,
        "Write the expression as (3/2) × (sin 3x)/(3x). Using limᵤ→₀ sin u/u = 1, the answer is 3/2."),
    Question("m2", "JEE Advanced", 2020, "Mathematics", "Matrices", "Determinants",
        "If A = [[1, 2], [3, 4]], what is det(A)?",
        listOf("2", "−2", "10", "−10"), 1,
        "For [[a,b],[c,d]], det(A) = ad − bc = 1×4 − 2×3 = −2.")
)

private enum class Page { Home, Practice, Mock, Progress }

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
    val prefs = remember { context.getSharedPreferences("rankroom", 0) }
    val attempts = remember {
        mutableStateMapOf<String, Boolean>().apply {
            prefs.all.forEach { (key, value) -> if (key.startsWith("q_") && value is Boolean) put(key.removePrefix("q_"), value) }
        }
    }
    var page by remember { mutableStateOf(Page.Home) }
    var search by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("All") }
    var exam by remember { mutableStateOf("All") }
    var mockIndex by remember { mutableIntStateOf(0) }
    var mockActive by remember { mutableStateOf(false) }
    var mockSubmitted by remember { mutableStateOf(false) }
    var mockSeconds by remember { mutableIntStateOf(1800) }
    val mockChoices = remember { mutableStateMapOf<String, Int>() }
    var mockCount by remember { mutableIntStateOf(prefs.getInt("mocks", 0)) }

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
    val filtered = bank.filter { q ->
        (subject == "All" || q.subject == subject) && (exam == "All" || q.exam == exam) &&
            (search.isBlank() || "${q.prompt} ${q.chapter} ${q.topic} ${q.year} ${q.subject}".contains(search, true))
    }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF087F8C),
        onPrimary = androidx.compose.ui.graphics.Color.White,
        background = androidx.compose.ui.graphics.Color(0xFFF1F4F6),
        surface = androidx.compose.ui.graphics.Color.White,
        onSurface = androidx.compose.ui.graphics.Color(0xFF14252D)
    )) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("rankroom", fontWeight = FontWeight.ExtraBold)
                        Text("JEE PRACTICE DESK", style = MaterialTheme.typography.labelSmall)
                    } }
                )
            },
            bottomBar = {
                NavigationBar {
                    listOf(Page.Home, Page.Practice, Page.Mock, Page.Progress).forEach { item ->
                        val label = when (item) { Page.Home -> "Home"; Page.Practice -> "Practice"; Page.Mock -> "Mock"; Page.Progress -> "Progress" }
                        val icon = when (item) { Page.Home -> Icons.Default.Home; Page.Practice -> Icons.Default.MenuBook; Page.Mock -> Icons.Default.Assessment; Page.Progress -> Icons.Default.Assessment }
                        NavigationBarItem(selected = page == item, onClick = { page = item }, icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (page) {
                    Page.Home -> {
                        item { Text("Your preparation, in focus", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                        item { Text("JEE Main + Advanced · Practice by subject and topic", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Solved", "$attempted"); Metric("Accuracy", if (attempted == 0) "—" else "$accuracy%"); Metric("Mocks", "$mockCount")
                        } }
                        item { SectionTitle("Subject readiness") }
                        items(listOf("Physics", "Chemistry", "Mathematics")) { s ->
                            val ids = bank.filter { it.subject == s }.map { it.id }
                            val tried = ids.filter { attempts.containsKey(it) }
                            val pct = if (tried.isEmpty()) 0 else tried.count { attempts[it] == true } * 100 / tried.size
                            Card {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(s, fontWeight = FontWeight.SemiBold); Text(if (tried.isEmpty()) "Not started" else "$pct%") }
                                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                        item { Button(onClick = { page = Page.Practice }, modifier = Modifier.fillMaxWidth()) { Text("Browse question bank") } }
                        item { OutlinedButton(onClick = { page = Page.Mock; mockActive = true }, modifier = Modifier.fillMaxWidth()) { Text("Start a 30-minute mini mock") } }
                        item { Text("Prototype content: example questions only, not a complete verified PYQ archive.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Page.Practice -> {
                        item { Text("Question bank", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                        item { OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, label = { Text("Search question, topic or year") }) }
                        item { Text("Exam", style = MaterialTheme.typography.labelLarge) }
                        item { ChoiceChips(listOf("All", "JEE Main", "JEE Advanced"), exam) { exam = it } }
                        item { Text("Subject", style = MaterialTheme.typography.labelLarge) }
                        item { ChoiceChips(listOf("All", "Physics", "Chemistry", "Mathematics"), subject) { subject = it } }
                        if (filtered.isEmpty()) item { Text("No matches. Try another search or filter.", modifier = Modifier.padding(18.dp)) }
                        items(filtered, key = { it.id }) { q ->
                            QuestionCard(q, attempts[q.id], onAnswer = { choice ->
                                val isCorrect = choice == q.answer
                                attempts[q.id] = isCorrect
                                prefs.edit().putBoolean("q_${q.id}", isCorrect).apply()
                            })
                        }
                    }
                    Page.Mock -> {
                        item { Text("Timed mini mock", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                        item { Text("Six sample questions across Physics, Chemistry and Mathematics. Demo scoring: +4 / −1.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (!mockActive) item { Button(onClick = { mockActive = true; mockSubmitted = false; mockIndex = 0; mockSeconds = 1800; mockChoices.clear() }) { Text("Start 30-minute test") } }
                        if (mockActive && !mockSubmitted) {
                            item { Text("Time left: ${mockSeconds / 60}:${(mockSeconds % 60).toString().padStart(2, '0')} · Question ${mockIndex + 1} of ${bank.size}", fontWeight = FontWeight.Bold) }
                            item { QuestionCard(bank[mockIndex], null, showSolution = false, selectedChoice = mockChoices[bank[mockIndex].id], onAnswer = { mockChoices[bank[mockIndex].id] = it }) }
                            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { mockIndex = (mockIndex - 1).coerceAtLeast(0) }, modifier = Modifier.weight(1f)) { Text("Previous") }
                                Button(onClick = { mockIndex = (mockIndex + 1).coerceAtMost(bank.lastIndex) }, modifier = Modifier.weight(1f)) { Text("Next") }
                            } }
                            item { Button(onClick = {
                                var score = 0
                                bank.forEach { q -> mockChoices[q.id]?.let { choice ->
                                    val ok = choice == q.answer
                                    attempts[q.id] = ok
                                    prefs.edit().putBoolean("q_${q.id}", ok).apply()
                                    if (ok) score += 4 else score -= 1
                                } }
                                mockCount++; prefs.edit().putInt("mocks", mockCount).apply(); mockSubmitted = true
                            }, modifier = Modifier.fillMaxWidth()) { Text("Submit test") } }
                        }
                        if (mockSubmitted) {
                            val score = bank.sumOf<Int> { q -> when (mockChoices[q.id]) { q.answer -> 4; null -> 0; else -> -1 } }
                            item { Card { Column(Modifier.padding(18.dp)) { Text("Test submitted", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Score: $score · ${mockChoices.size}/${bank.size} answered") } } }
                            item { Button(onClick = { page = Page.Practice }) { Text("Review questions and solutions") } }
                        }
                    }
                    Page.Progress -> {
                        item { Text("My progress", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                        item { Text("Saved on this device", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Solved", "$attempted"); Metric("Accuracy", if (attempted == 0) "—" else "$accuracy%"); Metric("Mocks", "$mockCount") } }
                        items(listOf("Physics", "Chemistry", "Mathematics")) { s ->
                            val qs = bank.filter { it.subject == s }; val done = qs.filter { attempts.containsKey(it.id) }
                            val pct = if (done.isEmpty()) 0 else done.count { attempts[it.id] == true } * 100 / done.size
                            Card { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("$s · ${done.size}/${qs.size} attempted", fontWeight = FontWeight.SemiBold)
                                LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
                                Text(if (done.isEmpty()) "Answer questions to see accuracy" else "$pct% accuracy", style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                        item { OutlinedButton(onClick = { prefs.edit().clear().apply(); attempts.clear(); mockCount = 0 }) { Text("Reset progress") } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Card(Modifier.weight(1f)) { Column(Modifier.padding(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    } }
}

@Composable
private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

@Composable
private fun ChoiceChips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(option) }) }
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
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${question.exam} ${question.year} · ${question.subject}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${question.chapter} · ${question.topic}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(question.prompt, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            question.choices.forEachIndexed { index, option ->
                val optionLabel = "${'A' + index}. $option"
                if (answered && showSolution) {
                    val iconText = if (index == question.answer) "✓ " else if (index == selected && previousCorrect == false) "× " else ""
                    TextButton(onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false) { Text(iconText + optionLabel, modifier = Modifier.fillMaxWidth(), color = if (index == question.answer) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
                } else {
                    OutlinedButton(onClick = { choice = index; if (showSolution) { answered = true; onAnswer(index) } else onAnswer(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text((if (selected == index) "● " else "○ ") + optionLabel, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            if (answered && showSolution) {
                Text(if (previousCorrect == true || (previousCorrect == null && choice == question.answer)) "Correct answer" else "Solution", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(question.explanation, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
