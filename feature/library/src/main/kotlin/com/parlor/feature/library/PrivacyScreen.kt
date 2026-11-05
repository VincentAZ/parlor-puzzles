package com.parlor.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.library_back))
        }
        Text(stringResource(R.string.library_privacy_policy), style = MaterialTheme.typography.headlineLarge)
        listOf(
            R.string.privacy_heading_0 to R.string.privacy_body_0,
            R.string.privacy_heading_1 to R.string.privacy_body_1,
            R.string.privacy_heading_2 to R.string.privacy_body_2,
            R.string.privacy_heading_3 to R.string.privacy_body_3,
            R.string.privacy_heading_4 to R.string.privacy_body_4,
        ).forEach { (heading, body) ->
            Text(stringResource(heading), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(body))
        }
    }
}
