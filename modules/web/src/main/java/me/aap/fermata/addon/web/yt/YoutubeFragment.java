package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.FermataWebClient.isYoutubeUri;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedVoid;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebBrowserAddon;
import me.aap.fermata.addon.web.WebBrowserFragment;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.service.FermataServiceUiBinder;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.view.VideoView;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.function.LongSupplier;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.PreferenceStore.Pref;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.menu.OverlayMenuItem;
import me.aap.utils.ui.view.ToolBarView;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class YoutubeFragment extends WebBrowserFragment implements FermataServiceUiBinder.Listener {
	static final String DEFAULT_URL = "https://m.youtube.com";
	private static final Set<String> DEFAULT_URLS = new HashSet<>(Arrays.asList(DEFAULT_URL, DEFAULT_URL + '/'));
	private static final Pref<LongSupplier> RESUME_POS = Pref.l("YT_RESUME_POS", 0L);
	private boolean playOnResume;

	@Override
	public int getFragmentId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.youtube, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
		YoutubeAddon addon = AddonManager.get().getAddon(YoutubeAddon.class);
		if (addon == null) return;

		String url;
		boolean pause;

		if (state != null) {
			url = state.getString("url", DEFAULT_URL);
			pause = state.getBoolean("pause", false);
		} else {
			url = DEFAULT_URL;
			pause = false;
		}

		MainActivityDelegate.getActivityDelegate(view.getContext()).onSuccess(a -> {
			YoutubeWebView webView = a.findViewById(R.id.ytWebView);
			VideoView videoView = a.findViewById(R.id.ytVideoView);
			YoutubeWebClient webClient = new YoutubeWebClient();
			YoutubeChromeClient chromeClient = new YoutubeChromeClient(webView, videoView);
			webView.init(addon, webClient, chromeClient);
			registerListeners(a);
			webView.loadUrl(DEFAULT_URL);
			if (!DEFAULT_URL.equals(url)) a.post(() -> webView.loadUrl(url));
			a.postDelayed(() -> {
				PreferenceStore ps = addon.getPreferenceStore();
				long pos = ps.getLongPref(RESUME_POS);
				ps.removePref(RESUME_POS);
				MediaSessionCallback cb = a.getMediaSessionCallback();
				if (cb.getEngine() instanceof YoutubeMediaEngine) {
					if (pos > 0L) cb.onSeekTo(pos);
					if (pause) cb.onPause();
				}
			}, 3000L);
		});
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle state) {
		super.onSaveInstanceState(state);
		String url = getUrl();
		if (url != null) state.putString("url", url);
		WebBrowserAddon addon = getAddon();
		if (addon == null) return;
		MainActivityDelegate a = MainActivityDelegate.getActivityDelegate(getContext()).peek();
		if (a == null) return;

		SharedPreferenceStore ps = addon.getPreferenceStore();
		MediaSessionCallback cb = a.getMediaSessionCallback();
		MediaEngine eng = cb.getEngine();

		if (eng instanceof YoutubeMediaEngine) {
			state.putBoolean("pause", !cb.isPlaying());
			eng.getPosition().onSuccess(pos -> ps.applyLongPref(RESUME_POS, pos));
		} else {
			ps.removePref(RESUME_POS);
		}
	}

	@Override
	public void onDestroyView() {
		unregisterListeners(MainActivityDelegate.get(requireContext()));
		super.onDestroyView();
	}

	@Override
	protected void registerListeners(MainActivityDelegate a) {
		super.registerListeners(a);
		a.getMediaServiceBinder().addBroadcastListener(this);
	}

	protected void unregisterListeners(MainActivityDelegate a) {
		super.unregisterListeners(a);
		a.getMediaServiceBinder().removeBroadcastListener(this);
	}

	@Override
	public void onPause() {
		if (!BuildConfig.AUTO) {
			MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
				FermataServiceUiBinder b = a.getMediaServiceBinder();
				if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem()) && b.isPlaying()) {
					b.getMediaSessionCallback().onPause();
					playOnResume = true;
				} else {
					playOnResume = false;
				}
			});
		}
		super.onPause();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (BuildConfig.AUTO || !playOnResume) return;
		playOnResume = false;
		MainActivityDelegate.getActivityDelegate(getContext()).onSuccess(a -> {
			FermataServiceUiBinder b = a.getMediaServiceBinder();
			if (YoutubeMediaEngine.isYoutubeItem(b.getCurrentItem())) {
				b.getMediaSessionCallback().onPlay();
			}
		});
	}

	public void loadUrl(String url) {
		FermataWebView v = getWebView();
		if (v != null) v.loadUrl(url);
	}

	@Override
	public void onPlayableChanged(MediaLib.PlayableItem oldItem, MediaLib.PlayableItem newItem) {
		if (isHidden()) return;

		if (YoutubeMediaEngine.isYoutubeItem(newItem)) {
			FermataWebView v = getWebView();
			MainActivityDelegate a = MainActivityDelegate.get(getContext());
			if (v == null) return;

			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome == null) return;

			if (!DEFAULT_URLS.contains(getUrl())) chrome.enterFullScreen();
		} else if (YoutubeMediaEngine.isYoutubeItem(oldItem)) {
			FermataWebView v = getWebView();
			if (v == null) return;
			FermataChromeClient chrome = v.getWebChromeClient();
			if (chrome != null) chrome.exitFullScreen();
		}
	}

	@Override
	public ToolBarView.Mediator getToolBarMediator() {
		return YoutubeToolBarMediator.getInstance();
	}

	@Override
	public boolean canScrollUp() {
		FermataWebView v = getWebView();
		if (v == null) return false;
		FermataChromeClient chrome = v.getWebChromeClient();
		return (chrome != null) && (chrome.isFullScreen() || (v.getScrollY() > 0));
	}

	@Nullable
	protected WebBrowserAddon getAddon() {
		return AddonManager.get().getAddon(YoutubeAddon.class);
	}

	@Nullable
	protected YoutubeWebView getWebView() {
		View v = getView();
		return (v != null) ? v.findViewById(R.id.ytWebView) : null;
	}

	protected boolean isDesktopVersionSupported() {
		return false;
	}

	@Nullable
	String getCurrentVideoId() {
		FermataWebView v = getWebView();
		if (v == null) return null;
		String url = v.getUrl();
		if (url == null) return null;

		Uri u = Uri.parse(url);
		if (!isYoutubeUri(u)) return null;

		String id = u.getQueryParameter("v");
		if ((id != null) && !id.isEmpty()) return id;

		String path = u.getPath();
		if (path != null && path.startsWith("/shorts/")) {
			String[] seg = path.split("/");
			if (seg.length >= 3 && !seg[2].isEmpty()) return seg[2];
		}

		return null;
	}

	@Override
	public void contributeToNavBarMenu(OverlayMenu.Builder b) {
		super.contributeToNavBarMenu(b);

		MainActivityDelegate a = MainActivityDelegate.get(requireContext());
		DefaultMediaLib lib = (DefaultMediaLib) a.getLib();
		MediaLib.Favorites favorites = lib.getFavorites();
		YoutubeVideoItem current = getCurrentVideoItem(lib);

		boolean isFav = (current != null) && current.isFavoriteItem();
		b.addItem(me.aap.fermata.R.id.favorites,
				isFav ? me.aap.fermata.R.drawable.favorite_filled : me.aap.fermata.R.drawable.favorite,
				me.aap.fermata.R.string.favorites)
				.setFutureSubmenu(sb -> favoritesMenu(sb, favorites, current));

		b.addItem(me.aap.fermata.R.id.playlists, me.aap.fermata.R.drawable.playlist,
				me.aap.fermata.R.string.playlists)
				.setFutureSubmenu(sb -> playlistsMenu(sb, lib, current));
	}

	@Nullable
	private YoutubeVideoItem getCurrentVideoItem(DefaultMediaLib lib) {
		String videoId = getCurrentVideoId();
		if (videoId == null) return null;

		YoutubeAddon addon = (YoutubeAddon) getAddon();
		FermataWebView v = getWebView();
		if ((addon == null) || (v == null)) return null;

		String title = v.getTitle();
		addon.cacheVideoTitle(videoId, ((title == null) || title.isEmpty()) ? videoId : title);
		return new YoutubeVideoItem(videoId, addon.getRootItem(lib));
	}

	private FutureSupplier<Void> favoritesMenu(OverlayMenu.Builder b, MediaLib.Favorites favorites,
																							@Nullable YoutubeVideoItem current) {
		if (current != null) {
			if (current.isFavoriteItem()) {
				b.addItem(me.aap.fermata.R.id.favorites_remove, me.aap.fermata.R.drawable.favorite_filled,
						me.aap.fermata.R.string.favorites_remove).setHandler(i -> {
					favorites.removeItem(current);
					return true;
				});
			} else {
				b.addItem(me.aap.fermata.R.id.favorites_add, me.aap.fermata.R.drawable.favorite,
						me.aap.fermata.R.string.favorites_add).setHandler(i -> {
					favorites.addItem(current);
					return true;
				});
			}
		}

		return favorites.getUnsortedChildren().main().then(list -> {
			int i = 0;
			for (MediaLib.Item it : list) {
				if (it instanceof MediaLib.ExternallyPlayableItem) {
					b.addItem(UiUtils.getArrayItemId(i++), it.getName()).setData(it)
							.setHandler(item -> favoriteItemSelected(item, favorites));
				}
			}
			return completedVoid();
		});
	}

	private boolean favoriteItemSelected(OverlayMenuItem item, MediaLib.Favorites favorites) {
		MediaLib.Item it = item.getData();
		if (item.isLongClick()) {
			item.getMenu().show(sb -> sb.addItem(me.aap.fermata.R.id.favorites_remove,
					me.aap.fermata.R.string.favorites_remove).setHandler(i -> {
				favorites.removeItem((MediaLib.PlayableItem) it);
				return true;
			}));
		} else if (it instanceof MediaLib.ExternallyPlayableItem ext) {
			ext.loadInFragment(this);
		}
		return true;
	}

	private FutureSupplier<Void> playlistsMenu(OverlayMenu.Builder b, DefaultMediaLib lib,
																							@Nullable YoutubeVideoItem current) {
		if (current != null) {
			List<MediaLib.PlayableItem> selection = Collections.singletonList(current);
			MainActivityDelegate.get(requireContext()).addPlaylistMenu(b, completed(selection));
		}

		return lib.getPlaylists().getUnsortedChildren().main().then(list -> {
			int i = 0;
			for (MediaLib.Item it : list) {
				if (it instanceof MediaLib.Playlist pl) {
					b.addItem(UiUtils.getArrayItemId(i++), it.getName())
							.setFutureSubmenu(sb -> playlistItemsMenu(sb, pl));
				}
			}
			return completedVoid();
		});
	}

	private FutureSupplier<Void> playlistItemsMenu(OverlayMenu.Builder b, MediaLib.Playlist playlist) {
		return playlist.getUnsortedChildren().main().then(list -> {
			for (int i = 0; i < list.size(); i++) {
				MediaLib.Item it = list.get(i);
				if (it instanceof MediaLib.ExternallyPlayableItem) {
					int idx = i;
					b.addItem(UiUtils.getArrayItemId(i), it.getName()).setData(it)
							.setHandler(item -> playlistItemSelected(item, playlist, idx));
				}
			}
			return completedVoid();
		});
	}

	private boolean playlistItemSelected(OverlayMenuItem item, MediaLib.Playlist playlist, int idx) {
		MediaLib.Item it = item.getData();
		if (item.isLongClick()) {
			item.getMenu().show(sb -> sb.addItem(me.aap.fermata.R.id.playlist_remove_item,
					me.aap.fermata.R.string.playlist_remove_item).setHandler(i -> {
				playlist.removeItem(idx);
				return true;
			}));
		} else if (it instanceof MediaLib.ExternallyPlayableItem ext) {
			ext.loadInFragment(this);
		}
		return true;
	}

	@Override
	protected String getSearchUrl() {
		return "https://www.youtube.com/results?search_query=";
	}
}
