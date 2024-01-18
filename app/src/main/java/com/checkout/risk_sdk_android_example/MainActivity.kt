package com.checkout.risk_sdk_android_example

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.checkout.risk_sdk_android.PublishDataResult
import com.checkout.risk_sdk_android.Risk
import com.checkout.risk_sdk_android.RiskConfig
import com.checkout.risk_sdk_android.RiskEnvironment
import com.checkout.risk_sdk_android.RiskInitialisationResult
import com.checkout.risk_sdk_android_example.ui.theme.RisksdkandroidTheme
import kotlinx.coroutines.launch

// import com.google.android.material.progressindicator.CircularProgressIndicator

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

enum class DataFetchStatus {
    LOADING,
    IDLE,
    ERROR,
    ;

    var errorMessage: String? = null

    fun setError(errorMessage: String?) {
        this.errorMessage = errorMessage
    }
}

@Composable
fun MyScreen(context: Context) {
    var riskInstance by remember { mutableStateOf<Risk?>(null) }
    var deviceSessionId by remember { mutableStateOf<String?>(null) }
    var openAlertDialog by remember {
        mutableStateOf(
            false,
        )
    }

    var status by remember {
        mutableStateOf(DataFetchStatus.IDLE)
    }

    LaunchedEffect("risk") {
        status = DataFetchStatus.LOADING
        riskInstance =
            Risk.getInstance(
                context,
                RiskConfig(
                    BuildConfig.SAMPLE_MERCHANT_PUBLIC_KEY,
                    RiskEnvironment.QA,
                    false,
                ),
            ).let {
                when (it) {
                    is RiskInitialisationResult.Success -> {
                        status = DataFetchStatus.IDLE
                        it.risk
                    }

                    is RiskInitialisationResult.Failure -> {
                        status = DataFetchStatus.ERROR
                        status.setError(it.message)
                        null
                    }

                    is RiskInitialisationResult.IntegrationDisabled -> {
                        status = DataFetchStatus.ERROR
                        status.setError("integration disabled")
                        null
                    }
                }
            }
    }

    val coroutineScope = rememberCoroutineScope()

    // A surface container using the 'background' color from the theme
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (status) {
                DataFetchStatus.LOADING -> {
                    CircularProgressIndicator()
                }

                DataFetchStatus.ERROR -> {
                    AlertDialogExample(
                        onDismissRequest = {
                            status.setError(null)
                            status = DataFetchStatus.IDLE
                        },
                        onConfirmation = {
                            status.setError(null)
                            status = DataFetchStatus.IDLE
                        },
                        dialogTitle = "Something went wrong!",
                        dialogText = status.errorMessage ?: "Unknown",
                    )
                }

                DataFetchStatus.IDLE -> {
                    Greeting()
                    PayButton(onClick = {
                        coroutineScope.launch {
                            riskInstance?.publishData().let {
                                if (it is PublishDataResult.Success) {
                                    deviceSessionId =
                                        it.deviceSessionId
                                    openAlertDialog = true
                                }
                            }
                        }
                    })
                    if (openAlertDialog) {
                        AlertDialogExample(
                            onDismissRequest = {
                                openAlertDialog = false
                            },
                            onConfirmation = {
                                openAlertDialog = false
                            },
                            dialogTitle = "Device Session ID",
                            dialogText = deviceSessionId ?: "Unknown",
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertDialogExample(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
) {
    AlertDialog(
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                },
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                },
            ) {
                Text("Dismiss")
            }
        },
    )
}

@Composable
fun PayButton(onClick: () -> Unit) {
    Button(onClick) {
        Text("Pay $25")
    }
}

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    Text(
        text = "Welcome!",
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RisksdkandroidTheme {
        Greeting()
    }
}
