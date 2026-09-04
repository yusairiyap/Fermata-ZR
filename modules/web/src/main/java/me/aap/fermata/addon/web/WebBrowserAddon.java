package me.aap.fermata.addon.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebViewDatabase;

import androidx.annotation.IdRes;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import me.aap.fermata.FermataApplication;
import me.aap.fermata.addon.AddonInfo;
import me.aap.fermata.addon.FermataAddon;
import me.aap.fermata.addon.FermataFragmentAddon;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.app.App;
import me.aap.utils.function.BooleanSupplier;
import me.aap.utils.function.IntSupplier;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.misc.ChangeableCondition;
import me.aap.utils.pref.PreferenceSet;
import me.aap.utils.pref.PreferenceStore;
import me.aap.utils.pref.SharedPreferenceStore;
import me.aap.utils.text.TextUtils;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * @author Andrey Pavlenko
 */
@Keep
@SuppressWarnings("unused")
public class WebBrowserAddon implements FermataFragmentAddon, SharedPreferenceStore {
	@NonNull
	private static final AddonInfo info = FermataAddon.findAddonInfo(WebBrowserAddon.class.getName());
	private static final Pref<Supplier<String>> LAST_URL = Pref.s("LAST_URL", "http://google.com");
	public static final int DARK_MODE_DISABLED = 0;
	public static final int DARK_MODE_ENABLED = 1;
	public static final int DARK_MODE_AUTO = 2;
	private static final Pref<IntSupplier> DARK_MODE = Pref.i("DARK_MODE", DARK_MODE_AUTO);
	private static final Pref<Supplier<String>> USER_AGENT = Pref.s("USER_AGENT",
			"Mozilla/5.0 (Linux; Android {ANDROID_VERSION}) " +
					"AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) " +
					"Chrome/{CHROME_VERSION} Mobile Safari/{WEBKIT_VERSION}");
	private static final Pref<Supplier<String>> USER_AGENT_DESKTOP = Pref.s("USER_AGENT_DESKTOP",
			"Mozilla/5.0 (X11; Linux x86_64) " +
					"AppleWebKit/{WEBKIT_VERSION} (KHTML, like Gecko) " +
					"Chrome/{CHROME_VERSION} Safari/{WEBKIT_VERSION}");
	private static final Pref<BooleanSupplier> DESKTOP_VERSION = Pref.b("DESKTOP_VERSION", false);
	private static final Pref<BooleanSupplier> WEB_OPEN_ON_START = Pref.b("WEB_OPEN_ON_START", false);
	private static final Pref<Supplier<String[]>> BOOKMARKS = Pref.sa("BOOKMARKS");
	// Cookie snapshot taken right before entering Private Mode, restored when leaving it (unless
	// the user asked to discard it too). Android's CookieManager has no "list all cookies" API, so
	// this is scoped to a handful of well-known origins rather than being a true full backup -- see
	// snapshotOrigins().
	private static final Pref<Supplier<String[]>> PRIVATE_MODE_SNAPSHOT = Pref.sa("PRIVATE_MODE_SNAPSHOT");
	private static final String[] SNAPSHOT_ORIGINS = {
			"https://youtube.com", "https://www.youtube.com", "https://m.youtube.com",
			"https://google.com", "https://www.google.com", "https://accounts.google.com",
	};
	private final SharedPreferences prefs;
	private boolean ignorePrefChange;
	// EventBroadcaster keeps listeners via WeakReference (ListenerRef extends WeakReference<L>), so
	// a bare `this::onPrivateModePrefsChanged` passed straight to addBroadcastListener() has nothing
	// else holding it alive and gets garbage-collected shortly after this constructor returns --
	// silently dropping the listener with no error. Keeping a strong reference in this field is what
	// keeps it registered for the addon's whole lifetime.
	private final PreferenceStore.Listener privateModeListener = this::onPrivateModePrefsChanged;

	public WebBrowserAddon() {
		prefs = App.get().getSharedPreferences("web", Context.MODE_PRIVATE);
		// Registered here rather than in contributeSettings() (only wired up once the user actually
		// opens Settings) so a Private Mode toggle flipped from the toolbar or the nav-bar menu -
		// without ever visiting Settings - still clears/restores browsing data.
		MainActivityPrefs.get().addBroadcastListener(privateModeListener);
		Log.i("WebBrowserAddon: Private Mode listener registered");
	}

	private void onPrivateModePrefsChanged(PreferenceStore store, List<Pref<?>> changed) {
		if (changed.contains(MainActivityPrefs.PRIVATE_MODE_ENABLED)) {
			MainActivityPrefs mp = MainActivityPrefs.get();
			if (mp.isPrivateModeEnabled()) {
				Log.i("Private Mode enabled: snapshotting cookies, then clearing browsing data");
				// Snapshot what's there *before* wiping it, so turning Private Mode back off can bring
				// the user's normal session back instead of just leaving them logged out everywhere.
				snapshotCookies();
				clearBrowsingData(mp::notifyPrivateModeDataCleared);
			} else if (mp.getBooleanPref(MainActivityPrefs.PRIVATE_MODE_CLEAR_ON_EXIT)) {
				Log.i("Private Mode disabled: clearing browsing data, discarding snapshot");
				clearBrowsingData(() -> {
					clearSnapshot();
					mp.notifyPrivateModeDataCleared();
				});
			} else {
				Log.i("Private Mode disabled: clearing browsing data, restoring snapshot");
				clearBrowsingData(() -> restoreCookieSnapshot(mp::notifyPrivateModeDataCleared));
			}
		}

		if (changed.contains(MainActivityPrefs.PRIVATE_MODE_CLEAR_REQUEST)) {
			// A manual "clear now" discards the pending snapshot too -- the user asked for everything
			// gone right now, not for a later mode-exit to quietly bring old cookies back.
			Log.i("Private Mode: manual clear-now requested");
			clearBrowsingData(() -> {
				clearSnapshot();
				MainActivityPrefs.get().notifyPrivateModeDataCleared();
			});
		}
	}

	/**
	 * Wipes cookies, DOM/IndexedDB/WebSQL storage and saved form data for every WebView in the
	 * app, then runs {@code onDone} -- there's no per-instance cookie jar in Android's WebView, so
	 * this is the closest approximation of "forget this session" available without a custom WebView
	 * data directory.
	 * <p>
	 * {@link CookieManager#removeAllCookies} is asynchronous despite returning immediately, so
	 * anything that must only happen once cookies are actually gone (e.g. a WebView reloading a
	 * page that must come back logged out) needs to wait for {@code onDone} rather than running
	 * right after calling this method -- otherwise the reload can race the clear and the request
	 * still goes out with the about-to-be-removed cookies attached.
	 */
	private void clearBrowsingData(Runnable onDone) {
		CookieManager cm = CookieManager.getInstance();
		cm.removeAllCookies(cleared -> {
			Log.i("Private Mode: cookies removed (hadAny=" + cleared + "), clearing storage/form data");
			cm.flush();
			WebStorage.getInstance().deleteAllData();
			try {
				WebViewDatabase.getInstance(FermataApplication.get()).clearFormData();
			} catch (Exception ex) {
				Log.e(ex, "Failed to clear WebView form data");
			}
			onDone.run();
		});
	}

	/**
	 * Captures the current cookie string for a handful of well-known origins (YouTube, Google
	 * sign-in, and whatever the Browser tab was last on) so they can be restored after a private
	 * session. This is a best-effort approximation, not a full backup: {@link CookieManager} only
	 * exposes cookies per-URL, not an enumeration of every domain that has one, and the restored
	 * cookies lose their original expiry/secure/httpOnly attributes (re-set as plain session
	 * cookies against the same origin).
	 */
	private void snapshotCookies() {
		CookieManager cm = CookieManager.getInstance();
		Map<String, String> snapshot = new LinkedHashMap<>();
		for (String origin : snapshotOrigins()) {
			String cookie = cm.getCookie(origin);
			if ((cookie != null) && !cookie.isEmpty()) snapshot.put(origin, cookie);
		}
		setSnapshot(snapshot);
	}

	private String[] snapshotOrigins() {
		String last = getLastUrl();
		if (TextUtils.isNullOrBlank(last)) return SNAPSHOT_ORIGINS;

		Uri u = Uri.parse(last);
		String scheme = u.getScheme();
		String host = u.getHost();
		if ((scheme == null) || (host == null)) return SNAPSHOT_ORIGINS;

		String origin = scheme + "://" + host;
		String[] origins = Arrays.copyOf(SNAPSHOT_ORIGINS, SNAPSHOT_ORIGINS.length + 1);
		origins[SNAPSHOT_ORIGINS.length] = origin;
		return origins;
	}

	private void restoreCookieSnapshot(Runnable onDone) {
		Map<String, String> snapshot = getSnapshot();
		if (!snapshot.isEmpty()) {
			CookieManager cm = CookieManager.getInstance();
			for (Map.Entry<String, String> e : snapshot.entrySet()) {
				for (String pair : e.getValue().split(";\\s*")) {
					if (!pair.isEmpty()) cm.setCookie(e.getKey(), pair);
				}
			}
			cm.flush();
			clearSnapshot();
		}
		onDone.run();
	}

	private Map<String, String> getSnapshot() {
		String[] p = getPreferenceStore().getStringArrayPref(PRIVATE_MODE_SNAPSHOT);
		if (p.length == 0) return Collections.emptyMap();

		Map<String, String> m = new LinkedHashMap<>(p.length);
		for (int i = 0; i < p.length; i++) {
			m.put(p[i], p[++i]);
		}
		return m;
	}

	private void setSnapshot(Map<String, String> m) {
		String[] p = new String[m.size() * 2];
		int i = 0;

		for (Map.Entry<String, String> e : m.entrySet()) {
			p[i++] = e.getKey();
			p[i++] = e.getValue();
		}

		getPreferenceStore().applyStringArrayPref(PRIVATE_MODE_SNAPSHOT, p);
	}

	private void clearSnapshot() {
		getPreferenceStore().applyStringArrayPref(PRIVATE_MODE_SNAPSHOT, new String[0]);
	}

	@IdRes
	@Override
	public int getAddonId() {
		return me.aap.fermata.R.id.web_browser_fragment;
	}

	@NonNull
	@Override
	public AddonInfo getInfo() {
		return info;
	}

	@NonNull
	@Override
	public ActivityFragment createFragment() {
		return new WebBrowserFragment();
	}

	@Override
	public void contributeSettings(Context ctx, PreferenceStore store, PreferenceSet set,
																 ChangeableCondition visibility) {
		getPreferenceStore().addBroadcastListener(this::onPreferenceChanged);
		FermataApplication.get().getPreferenceStore().addBroadcastListener(this::onPreferenceChanged);
		MainActivityPrefs.get().addBroadcastListener(this::onPreferenceChanged);
		set.addListPref(o -> {
			o.store = getPreferenceStore();
			o.pref = getForceDarkPref();
			o.title = R.string.force_dark;
			o.subtitle = R.string.force_dark_sub;
			o.visibility = visibility;
			o.formatSubtitle = true;
			o.values = new int[]{R.string.force_dark_disabled, R.string.force_dark_enabled, R.string.force_dark_auto};
		});

		if (getClass() == WebBrowserAddon.class) {
			set.addBooleanPref(o -> {
				o.store = getPreferenceStore();
				o.pref = WEB_OPEN_ON_START;
				o.title = R.string.open_on_start;
				o.visibility = visibility;
			});
			set.addStringPref(o -> {
				o.store = getPreferenceStore();
				o.pref = getUserAgentPref();
				o.title = R.string.user_agent;
				o.stringHint = o.pref.getDefaultValue().get();
				o.visibility = visibility;
				o.maxLines = 3;
			});
			set.addStringPref(o -> {
				o.store = getPreferenceStore();
				o.pref = getUserAgentDesktopPref();
				o.title = R.string.user_agent_desktop;
				o.stringHint = o.pref.getDefaultValue().get();
				o.visibility = visibility;
				o.maxLines = 3;
			});
		}
	}

	public void onPreferenceChanged(PreferenceStore store, List<Pref<?>> prefs) {
		if (ignorePrefChange) return;
		ignorePrefChange = true;

		if (prefs.contains(getInfo().enabledPref)) {
			if (!store.getBooleanPref(getInfo().enabledPref)) {
				MainActivityPrefs ap = MainActivityPrefs.get();
				getPreferenceStore().applyBooleanPref(WEB_OPEN_ON_START, false);
				if (getInfo().className.equals(ap.getShowAddonOnStartPref()))
					ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(WEB_OPEN_ON_START)) {
			MainActivityPrefs ap = MainActivityPrefs.get();
			if (store.getBooleanPref(WEB_OPEN_ON_START)) {
				ap.setShowAddonOnStartPref(getInfo().className);
			} else if (getInfo().className.equals(ap.getShowAddonOnStartPref())) {
				ap.setShowAddonOnStartPref(null);
			}
		} else if (prefs.contains(MainActivityPrefs.SHOW_ADDON_ON_START)) {
			getPreferenceStore().applyBooleanPref(WEB_OPEN_ON_START,
					getInfo().className.equals(MainActivityPrefs.get().getShowAddonOnStartPref()));
		}
		ignorePrefChange = false;
	}
	public SharedPreferenceStore getPreferenceStore() {
		return this;
	}

	private Collection<ListenerRef<Listener>> listeners;

	@NonNull
	@Override
	public SharedPreferences getSharedPreferences() {
		return prefs;
	}

	@Override
	public Collection<ListenerRef<Listener>> getBroadcastEventListeners() {
		return (listeners != null) ? listeners : (listeners = new LinkedList<>());
	}

	public Pref<IntSupplier> getForceDarkPref() {
		return DARK_MODE;
	}

	public Pref<Supplier<String>> getUserAgentPref() {
		return USER_AGENT;
	}

	public Pref<Supplier<String>> getUserAgentDesktopPref() {
		return USER_AGENT_DESKTOP;
	}

	public String getUserAgentDesktop() {
		Pref<Supplier<String>> p = getUserAgentDesktopPref();
		String ua = getPreferenceStore().getStringPref(p);
		return TextUtils.isNullOrBlank(ua) ? p.getDefaultValue().get() : ua;
	}

	public String getUserAgent() {
		Pref<Supplier<String>> p = getUserAgentPref();
		String ua = getPreferenceStore().getStringPref(p);
		return TextUtils.isNullOrBlank(ua) ? p.getDefaultValue().get() : ua;
	}

	public boolean isDisableDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 0;
	}

	public boolean isForceDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 1;
	}

	public boolean isAutoDark() {
		return getPreferenceStore().getIntPref(getForceDarkPref()) == 2;
	}

	public Pref<BooleanSupplier> getDesktopVersionPref() {
		return DESKTOP_VERSION;
	}

	public Pref<Supplier<String[]>> getBookmarksPref() {
		return BOOKMARKS;
	}

	public boolean isDesktopVersion() {
		return getPreferenceStore().getBooleanPref(getDesktopVersionPref());
	}

	public void setDesktopVersion(boolean v) {
		getPreferenceStore().applyBooleanPref(DESKTOP_VERSION, v);
	}

	Map<String, String> getBookmarks() {
		String[] p = getPreferenceStore().getStringArrayPref(getBookmarksPref());
		if (p.length == 0) return Collections.emptyMap();

		Map<String, String> m = new LinkedHashMap<>(p.length);
		for (int i = 0; i < p.length; i++) {
			m.put(p[i], p[++i]);
		}
		return m;
	}

	void addBookmark(String name, String url) {
		Map<String, String> m = getBookmarks();

		if (m.isEmpty()) {
			setBookmarks(Collections.singletonMap(url, name));
		} else {
			m.put(url, name);
			setBookmarks(m);
		}
	}

	void removeBookmark(String url) {
		Map<String, String> m = getBookmarks();

		if (!m.isEmpty()) {
			m.remove(url);
			setBookmarks(m);
		}
	}

	void setBookmarks(Map<String, String> m) {
		String[] p = new String[m.size() * 2];
		int i = 0;

		for (Map.Entry<String, String> e : m.entrySet()) {
			p[i++] = e.getKey();
			p[i++] = e.getValue();
		}

		getPreferenceStore().applyStringArrayPref(getBookmarksPref(), p);
	}

	String getLastUrl() {
		return getPreferenceStore().getStringPref(LAST_URL);
	}

	void setLastUrl(String url) {
		getPreferenceStore().applyStringPref(LAST_URL, url);
	}
}
