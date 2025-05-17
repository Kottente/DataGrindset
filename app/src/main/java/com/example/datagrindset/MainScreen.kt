package com.example.datagrindset.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.datagrindset.R

@Composable
fun MainScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color(0xFF22252A),
                title = { Text("Home", fontSize = 22.sp) },
                navigationIcon = {
                    IconButton(onClick = {/*TODO*/}) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = {/*TODO*/}) {
                        Icon(painterResource(R.drawable.ic_cast), contentDescription = "Cast")
                    }
                    IconButton(onClick = {/*TODO*/}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More")
                    }
                }
            )
        },
        backgroundColor = Color(0xFF181A20)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp)
        ) {
            Text("STORAGES", color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            StorageCard(
                //storage
                icon = painterResource(R.drawable.ic_storage),
                title = "Internal Storage",
                progress = 0.81f,
                freeText = "22.85 GB free"
            )
            Spacer(Modifier.height(8.dp))
            StorageCard(
                //memory
                icon = painterResource(R.drawable.ic_memory),
                title = "Processes",
                progress = 0.60f,
                freeText = "3.24 GB free",
                iconTint = Color(0xFFF06292)
            )
            Spacer(Modifier.height(20.dp))
            Text("TOOLS", color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ToolIconsRow(
                listOf(
                    //cloudQueue
                    ToolIcon(painterResource(R.drawable.ic_connections), "Connections"),
                    //laptop
                    ToolIcon(painterResource(R.drawable.ic_laptop), "Transfer to PC"),
                    //wifi
                    ToolIcon(painterResource(R.drawable.ic_wifi), "WiFi Share"),
                    //cast
                    ToolIcon(painterResource(R.drawable.ic_cast), "Cast Queue"),
                    //android
                    ToolIcon(painterResource(R.drawable.ic_anroid), "User Apps")
                )
            )
            Spacer(Modifier.height(20.dp))
            Text("MEDIA", color = Color.Gray, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            MediaIconsGrid(

                listOf(
                    //fileDownload
                    MediaIcon(painterResource(R.drawable.ic_file_downloader ), "Downloads"),
                    MediaIcon(painterResource(R.drawable.ic_image), "Images"),
                    MediaIcon(painterResource(R.drawable.ic_movie), "Video"),
                    MediaIcon(painterResource(R.drawable.ic_music_note), "Audio"),
                    MediaIcon(painterResource(R.drawable.ic_description), "Documents"),
                    MediaIcon(painterResource(R.drawable.ic_archive), "Archives"),
                    MediaIcon(painterResource(R.drawable.ic_apk), "APK"),
                    MediaIcon(painterResource(R.drawable.ic_telegram), "Telegram"),
                    MediaIcon(painterResource(R.drawable.ic_twitter), "Twitter"),
                    MediaIcon(painterResource(R.drawable.ic_camera), "Screenshots"),
                    MediaIcon(painterResource(R.drawable.ic_instagram), "Instagram"),
                )
            )
        }
    }
}

@Composable
fun StorageCard(
    icon: Painter,
    title: String,
    progress: Float,
    freeText: String,
    iconTint: Color = Color(0xFF90CAF9)
) {
    Card(
        backgroundColor = Color(0xFF22252A),
        shape = MaterialTheme.shapes.medium,
        elevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = progress,
                    color = Color(0xFF64B5F6),
                    backgroundColor = Color(0xFF373A43),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(freeText, color = Color(0xFFB0BEC5), fontSize = 13.sp)
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                painterResource(R.drawable.ic_pie_chart),
                contentDescription = null,
                tint = Color(0xFFB0BEC5),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

data class ToolIcon(val icon: Painter, val label: String)
data class MediaIcon(val icon: Painter, val label: String)

@Composable
fun ToolIconsRow(tools: List<ToolIcon>) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        tools.forEach {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF23262C),
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        it.icon,
                        contentDescription = it.label,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(11.dp),
                        tint = Color(0xFF90CAF9)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(it.label, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun MediaIconsGrid(media: List<MediaIcon>) {
    val rows = media.chunked(5)
    Column {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF23262C),
                            modifier = Modifier.size(54.dp)
                        ) {
                            Icon(
                                it.icon,
                                contentDescription = it.label,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(11.dp),
                                tint = Color(0xFF90CAF9)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(it.label, color = Color.White, fontSize = 13.sp)
                    }
                }
                // Fill empty cells if not enough icons
                if (row.size < 5) {
                    repeat(5 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}