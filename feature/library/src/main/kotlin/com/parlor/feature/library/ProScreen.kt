package com.parlor.feature.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.parlor.core.platform.billing.*

@Composable
fun ProScreen(state: ProState, onBack: () -> Unit, onBuy: () -> Unit, onRestore: () -> Unit, onRedeem: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.library_back)) }
        Text(stringResource(R.string.library_pro_title),style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.library_pro_benefits))
        if(state.isPro) Text(stringResource(R.string.library_pro_active),style = MaterialTheme.typography.titleLarge)
        Text(stringResource(when(state.notice) {
            PurchaseNotice.CHECKING -> R.string.library_purchase_checking
            PurchaseNotice.UNAVAILABLE -> R.string.library_purchase_unavailable
            PurchaseNotice.PENDING -> R.string.library_purchase_pending
            PurchaseNotice.ACKNOWLEDGING -> R.string.library_purchase_finishing
            PurchaseNotice.RETRY -> if(state.isPro) R.string.library_purchase_offline_pro else R.string.library_purchase_retry
            PurchaseNotice.CANCELLED -> R.string.library_purchase_cancelled
            PurchaseNotice.RESTORED -> R.string.library_purchase_restored
            PurchaseNotice.NOT_FOUND -> R.string.library_purchase_not_found
            PurchaseNotice.PRICE_CHANGED -> R.string.library_purchase_price_changed
            PurchaseNotice.OPENING -> R.string.library_purchase_opening
            PurchaseNotice.READY -> if(state.isPro) R.string.library_purchase_owned else R.string.library_purchase_once
        }),modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        if(state.busy) CircularProgressIndicator()
        if(!state.isPro) {
            Button(onClick = onBuy,enabled = state.canBuy,modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                Text(state.offer?.let { stringResource(R.string.library_purchase_price,it.price) } ?: stringResource(R.string.library_purchase_no_price))
            }
        }
        OutlinedButton(onClick = onRestore,enabled = !state.busy && state.notice != PurchaseNotice.UNAVAILABLE,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            colors = ButtonDefaults.outlinedButtonColors(disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
            Text(stringResource(R.string.library_purchase_restore))
        }
        TextButton(onClick = onRedeem) { Text(stringResource(R.string.library_purchase_redeem)) }
        Text(stringResource(R.string.library_purchase_restore_detail))
    }
}
