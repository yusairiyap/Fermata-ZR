package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import me.aap.fermata.ui.view.VideoInfoView;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeVideoView extends VideoView {

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
		// YouTube's own page already shows the video title; Fermata's native info bar is
		// redundant here (and would otherwise show the internal placeholder item's class name
		// as a bogus "parent" description), so keep it permanently hidden for this video view.
		getVideoInfoView().setVisibility(GONE);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}
}
