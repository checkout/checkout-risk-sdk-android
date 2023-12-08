package com.checkout.risk_sdk_android_example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.checkout.risk_sdk_android.Risk
import com.checkout.risk_sdk_android.RiskConfig
import com.checkout.risk_sdk_android.RiskEnvironment
import com.checkout.risk_sdk_android_example.ui.theme.RisksdkandroidTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            RisksdkandroidTheme {
                MyScreen(this)
            }
        }
    }
}

@Composable
fun MyScreen(context: Context) {
    var riskInstance by remember { mutableStateOf<Risk?>(null) }

    LaunchedEffect("risk") {
        riskInstance =
            Risk.getInstance(context, RiskConfig("pk_public_key", RiskEnvironment.QA, false))
    }

    val coroutineScope = rememberCoroutineScope()

    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (riskInstance != null) {
                Greeting("Hello!")
                PayButton(onClick = {
                    coroutineScope.launch {
                        riskInstance?.publishData()
                    }
                })
            } else {
                // Handle the case when riskInstance is null (e.g., show loading state)
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
fun PayButton(onClick: () -> Unit) {
    Button(onClick) {
        Text("Pay $25")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RisksdkandroidTheme {
        Greeting("Android")
    }
}