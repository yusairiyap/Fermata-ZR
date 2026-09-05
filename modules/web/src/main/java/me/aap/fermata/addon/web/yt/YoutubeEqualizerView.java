package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.appcompat.widget.SwitchCompat;

import java.util.List;

import me.aap.utils.function.IntConsumer;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.PreferenceView;
import me.aap.utils.pref.PreferenceView.ListOpts;

/**
 * The YouTube-tab Equalizer/Bass Boost/Virtualizer panel, shown from the video menu. Reuses the
 * native {@code audio_effects.xml}/{@code equalizer_band.xml} layouts (this module already
 * depends on {@code :fermata}) but binds them to {@link YoutubeAddon}'s Web-Audio-backed prefs
 * instead of an {@link android.media.audiofx.AudioEffects} instance, since YouTube plays through
 * the WebView's own audio pipeline rather than one of the app's native engines. Unlike the native
 * panel, settings here are global to the YouTube tab (no per-track/per-folder scope), so the
 * "apply to"/Loudness-Enhancer sections of the shared layout are hidden.
 */
final class YoutubeEqualizerView extends android.widget.ScrollView implements PreferenceStore.Listener {
	@Nullable
	private YoutubeAddon addon;
	@Nullable
	private YoutubeWebView web;

	public YoutubeEqualizerView(Context context) {
		this(context, null);
		setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
	}

	public YoutubeEqualizerView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setBackgroundColor(Color.TRANSPARENT);
	}

	void init(YoutubeWebView web) {
		this.web = web;
		YoutubeAddon addon = this.addon = web.getAddon();
		inflate(getContext(), me.aap.fermata.R.layout.audio_effects, this);
		hide(me.aap.fermata.R.id.vol_boost, me.aap.fermata.R.id.apply_to,
				me.aap.fermata.R.id.virtualizer_mode, me.aap.fermata.R.id.equalizer_preset_save,
				me.aap.fermata.R.id.equalizer_preset_delete);
		addon.getPreferenceStore().addBroadcastListener(this);

		SwitchCompat eqSwitch = findViewById(me.aap.fermata.R.id.equalizer_switch);
		eqSwitch.setChecked(addon.eqEnabled());
		eqSwitch.setOnCheckedChangeListener((b, checked) -> {
			addon.setEqEnabled(checked);
			push();
		});
		createBands(addon);

		String[] names = new String[YoutubeEqualizerPresets.PRESET_NAMES.length + 1];
		names[0] = getResources().getString(me.aap.fermata.R.string.eq_manual);
		for (int i = 0; i < YoutubeEqualizerPresets.PRESET_NAMES.length; i++) {
			names[i + 1] = getResources().getString(YoutubeEqualizerPresets.PRESET_NAMES[i]);
		}

		PreferenceView presetView = findViewById(me.aap.fermata.R.id.equalizer_preset);
		presetView.setPreference(null, () -> {
			ListOpts o = new ListOpts();
			o.store = addon.getPreferenceStore();
			o.pref = YoutubeAddon.YT_EQ_PRESET;
			o.title = me.aap.fermata.R.string.string_format;
			o.formatTitle = true;
			o.stringValues = names;
			return o;
		});

		SwitchCompat virtSwitch = findViewById(me.aap.fermata.R.id.virtualizer_switch);
		virtSwitch.setChecked(addon.virtEnabled());
		virtSwitch.setOnCheckedChangeListener((b, checked) -> {
			addon.setVirtEnabled(checked);
			push();
		});
		configureSeek(findViewById(me.aap.fermata.R.id.virtualizer_seek), addon.virtStrength(),
				progress -> {
					addon.setVirtStrength(progress);
					push();
				});

		SwitchCompat bassSwitch = findViewById(me.aap.fermata.R.id.bass_switch);
		bassSwitch.setChecked(addon.bassEnabled());
		bassSwitch.setOnCheckedChangeListener((b, checked) -> {
			addon.setBassEnabled(checked);
			push();
		});
		configureSeek(findViewById(me.aap.fermata.R.id.bass_seek), addon.bassStrength(),
				progress -> {
					addon.setBassStrength(progress);
					push();
				});
	}

	void cleanup() {
		if (addon != null) addon.getPreferenceStore().removeBroadcastListener(this);
		removeAllViews();
		addon = null;
		web = null;
	}

	private void createBands(YoutubeAddon addon) {
		int[] bands = addon.eqBands();
		int range = YoutubeEqualizerPresets.BAND_MAX - YoutubeEqualizerPresets.BAND_MIN;
		String minText = String.valueOf(YoutubeEqualizerPresets.BAND_MIN / 100);
		String maxText = String.valueOf(YoutubeEqualizerPresets.BAND_MAX / 100);
		ViewGroup bandsView = findViewById(me.aap.fermata.R.id.equalizer_bands);
		LayoutInflater inflater = LayoutInflater.from(getContext());

		for (int i = 0; i < YoutubeEqualizerPresets.NUM_BANDS; i++) {
			inflater.inflate(me.aap.fermata.R.layout.equalizer_band, bandsView);
			ViewGroup bandView = (ViewGroup) bandsView.getChildAt(i);
			TextView label = bandView.findViewById(me.aap.fermata.R.id.eq_band_title);
			AppCompatSeekBar sb = bandView.findViewById(me.aap.fermata.R.id.eq_band_seek);
			TextView min = bandView.findViewById(me.aap.fermata.R.id.eq_band_min);
			TextView max = bandView.findViewById(me.aap.fermata.R.id.eq_band_max);
			int band = i;
			sb.setMax(range);
			sb.setProgress(bands[i] - YoutubeEqualizerPresets.BAND_MIN);
			min.setText(minText);
			max.setText(maxText);
			sb.setOnSeekBarChangeListener(new SeekBarListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (!fromUser) return;
					bandChanged(band, (short) (progress + YoutubeEqualizerPresets.BAND_MIN));
				}
			});

			float freq = YoutubeEqualizerPresets.CENTER_FREQ_HZ[i];
			if (freq >= 1000) {
				freq /= 1000;
				String s = String.format((freq == Math.floor(freq)) ? "%.0f" : "%.1f", freq);
				label.setText(getResources().getString(me.aap.fermata.R.string.eq_khz, s));
			} else {
				label.setText(getResources().getString(me.aap.fermata.R.string.eq_hz,
						String.valueOf((int) freq)));
			}
		}
	}

	private void bandChanged(int band, short level) {
		YoutubeAddon addon = this.addon;
		if (addon == null) return;
		int[] bands = addon.eqBands();
		bands[band] = level;
		addon.setEqBands(bands);
		if (addon.eqPreset() != 0) addon.setEqPreset(0);
		push();
	}

	private void setBandValues(int[] bands) {
		ViewGroup bandsView = findViewById(me.aap.fermata.R.id.equalizer_bands);
		for (int i = 0; i < YoutubeEqualizerPresets.NUM_BANDS; i++) {
			ViewGroup bandView = (ViewGroup) bandsView.getChildAt(i);
			AppCompatSeekBar sb = bandView.findViewById(me.aap.fermata.R.id.eq_band_seek);
			sb.setProgress(bands[i] - YoutubeEqualizerPresets.BAND_MIN);
		}
	}

	private void configureSeek(SeekBar sb, int progress, IntConsumer onChange) {
		sb.setMax(1000);
		sb.setProgress(progress);
		sb.setOnSeekBarChangeListener(new SeekBarListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				if (fromUser) onChange.accept(progress);
			}
		});
	}

	private void push() {
		if (web != null) web.configureEqualizer();
	}

	private void hide(@IdRes int... ids) {
		for (int id : ids) {
			View v = findViewById(id);
			if (v != null) v.setVisibility(GONE);
		}
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		YoutubeAddon addon = this.addon;
		if ((addon == null) || !prefs.contains(YoutubeAddon.YT_EQ_PRESET)) return;

		int preset = addon.eqPreset();
		if (preset == 0) return;

		int idx = preset - 1;
		if ((idx < 0) || (idx >= YoutubeEqualizerPresets.PRESETS.length)) return;

		int[] bands = YoutubeEqualizerPresets.PRESETS[idx].clone();
		addon.setEqBands(bands);
		setBandValues(bands);
		push();
	}

	private static abstract class SeekBarListener implements SeekBar.OnSeekBarChangeListener {
		public void onStartTrackingTouch(SeekBar seekBar) {
		}

		public void onStopTrackingTouch(SeekBar seekBar) {
		}
	}
}
