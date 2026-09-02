package me.aap.fermata.addon.web.yt;

import static me.aap.utils.async.Completed.completed;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.ExtRoot;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.media.lib.MediaLib.PlayableItem;
import me.aap.utils.async.FutureSupplier;

/**
 * Root of the YouTube branch in the media library's browse tree (used to resolve
 * {@code youtube:<videoId>} item ids). Lists the user's saved YouTube favourites as a browsable
 * shortcut, so Android Auto's native media-browser tree (voice / steering-wheel controls) has
 * something to show under it.
 */
class YoutubeRootItem extends ExtRoot {

	YoutubeRootItem(DefaultMediaLib lib) {
		super("youtube", lib);
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		me.aap.fermata.media.lib.MediaLib.Favorites favorites = getLib().getFavorites();
		FutureSupplier<List<Item>> children = favorites.getUnsortedChildren();
		return children.map(list -> {
			List<Item> yt = new ArrayList<>();
			for (Item i : list) {
				if ((i instanceof PlayableItem pi) && pi.getOrigId().startsWith("youtube:")) yt.add(i);
			}
			return yt;
		});
	}

	@NonNull
	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	@Override
	protected FutureSupplier<String> buildTitle() {
		return completed(getLib().getContext().getString(me.aap.fermata.R.string.addon_name_youtube));
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return completed("");
	}
}
