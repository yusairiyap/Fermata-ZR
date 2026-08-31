package me.aap.fermata.addon.web.yt;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static me.aap.fermata.media.pref.MediaPrefs.MEDIA_ENG_YT;
import static me.aap.utils.async.Completed.completed;

import android.support.v4.media.MediaMetadataCompat;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;

import me.aap.fermata.media.lib.ExtPlayable;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.vfs.generic.GenericFileSystem;

/**
 * A stable, ID-resolvable YouTube video, so it can be saved in and later resolved back from
 * Favourites/Playlists. Unlike {@code YoutubeMediaEngine.YoutubeItem}, which is a transient
 * now-playing placeholder, this item's id encodes the video id, and it is registered with the
 * media library via {@link YoutubeAddon} ({@code youtube:<videoId>}).
 */
public class YoutubeVideoItem extends ExtPlayable implements MediaLib.ExternallyPlayableItem {
	private final String videoId;

	public YoutubeVideoItem(String videoId, @NonNull BrowsableItem parent) {
		super("youtube:" + videoId, parent,
				GenericFileSystem.getInstance().create(watchUrl(videoId)));
		this.videoId = videoId;
	}

	static String watchUrl(String videoId) {
		return "https://m.youtube.com/watch?v=" + videoId;
	}

	public String getVideoId() {
		return videoId;
	}

	@Override
	public boolean isVideo() {
		return true;
	}

	@Override
	public boolean isSeekable() {
		return true;
	}

	@Override
	public int getVideoEnginePref() {
		return MEDIA_ENG_YT;
	}

	@IdRes
	@Override
	public int getPlayerFragmentId() {
		return me.aap.fermata.R.id.youtube_fragment;
	}

	@Override
	public void loadInFragment(ActivityFragment fragment) {
		((YoutubeFragment) fragment).loadUrl(watchUrl(videoId));
	}

	@NonNull
	@Override
	protected FutureSupplier<MediaMetadataCompat> loadMeta() {
		YoutubeAddon addon = me.aap.fermata.addon.AddonManager.get().getAddon(YoutubeAddon.class);
		String title = (addon != null) ? addon.getVideoTitle(videoId) : videoId;
		MediaMetadataCompat.Builder b = new MediaMetadataCompat.Builder();
		b.putString(METADATA_KEY_TITLE, title);
		b.putString(METADATA_KEY_ALBUM_ART_URI,
				"https://img.youtube.com/vi/" + videoId + "/hqdefault.jpg");
		return completed(b.build());
	}

	@Override
	protected String buildSubtitle(MediaMetadataCompat md, SharedTextBuilder tb) {
		return null;
	}
}
