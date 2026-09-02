package me.aap.fermata.addon.web.yt;

import static androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.RIGHT;

import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.WebToolBarMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.ToolBarView;

/**
 * Visible, car-friendly toolbar for the YouTube tab: reuses the standard web browser toolbar
 * (back/forward, address/search bar, bookmarks) and adds a "home" button, so it is easy to
 * target with the Android Auto DPAD cursor instead of being fully hidden.
 */
public class YoutubeToolBarMediator extends WebToolBarMediator {
	private static final YoutubeToolBarMediator instance = new YoutubeToolBarMediator();

	public static YoutubeToolBarMediator getInstance() {
		return instance;
	}

	@Override
	public void enable(ToolBarView tb, ActivityFragment f) {
		super.enable(tb, f);
		YoutubeFragment yt = (YoutubeFragment) f;
		addButton(tb, R.drawable.browser_home, v -> yt.loadUrl(YoutubeFragment.DEFAULT_URL),
				R.id.browser_home, RIGHT);
		addButton(tb, me.aap.fermata.R.drawable.favorite, v -> yt.showFavoritesMenu(),
				me.aap.fermata.R.id.favorites, RIGHT);
		addButton(tb, me.aap.fermata.R.drawable.playlist, v -> yt.showPlaylistsMenu(),
				me.aap.fermata.R.id.playlists, RIGHT);
	}
}
