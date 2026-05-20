package com.nekolaska.calabiyau

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri

class CrashReportActivity : ComponentActivity() {

    private var crashLog: String = ""

    companion object {
        private const val GITHUB_ISSUE_URL =
            "https://github.com/znzsofficial/CalabiYauVoice_GUI/issues/new?labels=bug&title=%5BCrash%5D+"
        private const val STATE_CRASH_LOG = "state_crash_log"
        private const val EXTRA_SKIP_PENDING_CRASH = "extra_skip_pending_crash"

        fun shouldSkipPendingCrash(intent: Intent?): Boolean =
            intent?.getBooleanExtra(EXTRA_SKIP_PENDING_CRASH, false) == true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        crashLog = savedInstanceState?.getString(STATE_CRASH_LOG)
            ?: intent.getStringExtra(CrashHandler.EXTRA_CRASH_LOG)
            ?: CrashHandler.peekCrashLog(this)
            ?: "未找到崩溃日志"

        if (savedInstanceState == null) {
            CrashHandler.clearCrashLog(this)
        }

        if (crashLog == "未找到崩溃日志") {
            finish()
            return
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CrashReportScreen(
                    crashLog = crashLog,
                    onCopy = { copyToClipboard(crashLog) },
                    onReport = { openGitHubIssue(crashLog) },
                    onRestart = { restartApp() }
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CRASH_LOG, crashLog)
        super.onSaveInstanceState(outState)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("崩溃日志", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun openGitHubIssue(log: String) {
        copyToClipboard(log)
        try {
            startActivity(Intent(Intent.ACTION_VIEW, GITHUB_ISSUE_URL.toUri()))
        } catch (_: Exception) {
            Toast.makeText(this, "无法打开浏览器，日志已复制到剪贴板。", Toast.LENGTH_LONG).show()
        }
    }

    private fun restartApp() {
        CrashHandler.clearCrashLog(this)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(EXTRA_SKIP_PENDING_CRASH, true)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CrashReportScreen(
    crashLog: String,
    onCopy: () -> Unit,
    onReport: () -> Unit,
    onRestart: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.BugReport,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("应用发生崩溃", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "应用遇到了未预期错误。你可以复制崩溃日志并反馈给开发者。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("复制崩溃日志")
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReport,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("反馈问题")
                }
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("重新启动")
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SelectionContainer {
                    Text(
                        text = crashLog,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
