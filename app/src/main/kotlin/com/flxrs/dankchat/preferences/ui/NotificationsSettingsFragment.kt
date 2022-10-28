package com.flxrs.dankchat.preferences.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.flxrs.dankchat.R
import com.flxrs.dankchat.databinding.HighlightsBottomsheetBinding
import com.flxrs.dankchat.databinding.MultiEntryBottomsheetBinding
import com.flxrs.dankchat.databinding.SettingsFragmentBinding
import com.flxrs.dankchat.preferences.multientry.MultiEntryAdapter
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto.Companion.toDto
import com.flxrs.dankchat.preferences.multientry.MultiEntryDto.Companion.toEntryItem
import com.flxrs.dankchat.preferences.multientry.MultiEntryItem
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsMenuAdapter
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsMenuTab
import com.flxrs.dankchat.preferences.ui.highlights.HighlightsViewModel
import com.flxrs.dankchat.utils.extensions.decodeOrNull
import com.flxrs.dankchat.utils.extensions.expand
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

@AndroidEntryPoint
class NotificationsSettingsFragment : MaterialPreferenceFragmentCompat() {

    private val viewModel: HighlightsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = SettingsFragmentBinding.bind(view)
        (requireActivity() as AppCompatActivity).apply {
            setSupportActionBar(binding.settingsToolbar)
            supportActionBar?.apply {
                setDisplayHomeAsUpEnabled(true)
                title = getString(R.string.preference_notifications_mentions_header)
            }
        }

        val preferences = PreferenceManager.getDefaultSharedPreferences(view.context)
        findPreference<Preference>(getString(R.string.preference_custom_mentions_key))?.apply {
            setOnPreferenceClickListener {
                showHighlightsSheet(view)
                true
            }
        }
        findPreference<Preference>(getString(R.string.preference_blacklist_key))?.apply {
            setOnPreferenceClickListener { showMultiEntryPreference(view, key, preferences, title) }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.notifications_settings, rootKey)
    }

    private fun showHighlightsSheet(root: View) {
        val context = root.context
        val binding = HighlightsBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            highlightsSheet.updateLayoutParams {
                height = resources.displayMetrics.heightPixels
            }
        }
        val adapter = HighlightsMenuAdapter(
            addItem = { viewModel.addHighlight(HighlightsMenuTab.values()[binding.highlightsTabs.selectedTabPosition]) },
            deleteItem = viewModel::removeHighlight,
        )
        with(binding) {
            highlightsViewPager.adapter = adapter
            TabLayoutMediator(highlightsTabs, highlightsViewPager) { tab, pos ->
                val highlightTab = HighlightsMenuTab.values()[pos]
                tab.text = when (highlightTab) {
                    // TODO
                    HighlightsMenuTab.Messages -> "Messages"
                    HighlightsMenuTab.Users    -> "Users"
                }
            }.attach()
        }
        BottomSheetDialog(context).apply {
            viewModel.fetchHighlights()
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.highlightTabs.collect {
                        adapter.submitList(it)
                    }
                }
            }
            setOnDismissListener {
                viewModel.updateHighlights(adapter.currentList)
            }
            setContentView(binding.root)
            behavior.skipCollapsed = true
            behavior.isFitToContents = false
            behavior.expand()
            show()
        }
    }

    private fun showMultiEntryPreference(root: View, key: String, sharedPreferences: SharedPreferences, title: CharSequence?): Boolean {
        val context = root.context
        val windowHeight = resources.displayMetrics.heightPixels
        val peekHeight = (windowHeight * 0.6).roundToInt()
        val entries = runCatching {
            sharedPreferences
                .getStringSet(key, emptySet())
                .orEmpty()
                .mapNotNull { Json.decodeOrNull<MultiEntryDto>(it) }
                .map { it.toEntryItem() }
                .sortedBy { it.entry }
                .plus(MultiEntryItem.AddEntry)
        }.getOrDefault(emptyList())

        val entryAdapter = MultiEntryAdapter(entries.toMutableList())
        val binding = MultiEntryBottomsheetBinding.inflate(LayoutInflater.from(context), root as? ViewGroup, false).apply {
            multiEntryTitle.text = title ?: ""
            multiEntryList.adapter = entryAdapter
            multiEntrySheet.updateLayoutParams {
                height = windowHeight
            }
        }

        BottomSheetDialog(context).apply {
            setContentView(binding.root)
            setOnDismissListener {
                val stringSet = entryAdapter.entries
                    .filterIsInstance<MultiEntryItem.Entry>()
                    .filter { it.entry.isNotBlank() }
                    .map { Json.encodeToString(it.toDto()) }
                    .toSet()

                sharedPreferences.edit { putStringSet(key, stringSet) }
            }
            behavior.isFitToContents = false
            behavior.peekHeight = peekHeight
            show()
        }

        return true
    }
}