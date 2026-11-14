package de.dokumentenpilot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                DokumentenPilotApp()
            }
        }
    }
}

@Composable
fun DokumentenPilotApp() {
    var dokumente by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Text(
                text = "DokumentenPilot",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Meine Dokumente")
            Text("Dokumente: $dokumente")

            Button(
                onClick = { dokumente++ }
            ) {
                Text("Testdokument hinzufügen")
            }

            Text(
                "Dies ist die erste Testversion."
            )
        }
    }
}
