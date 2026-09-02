package me.aap.fermata.ui.fragment;

import static android.os.SystemClock.uptimeMillis;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CHANGED;
import static me.aap.utils.ui.activity.ActivityListener.FRAGMENT_CONTENT_CHANGED;

import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.R;
import me.aap.fermata.action.Action;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.activity.MainActivityPrefs;
import me.aap.utils.ui.UiUtils;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.menu.OverlayMenu;
import me.aap.utils.ui.view.FloatingButton;

/**
 * @author Andrey Pavlenko
 */
public final class SecondaryFabMediator implements FloatingButton.Mediator,
		View.OnClickListener, View.OnLongClickListener {
	public static final SecondaryFabMediator instance = new SecondaryFabMediator();

	private static final List<Action> OFFERED_ACTIONS = List.of(
			Action.FULLSCREEN_TOGGLE, Action.VOLUME_MUTE_UNMUTE, Action.PLAY_PAUSE, Action.DIM_TOGGLE);

	private SecondaryFabMediator() {}

	@Override
	public void enable(FloatingButton fb, ActivityFragment f) {
		updateIcon(fb);
		fb.setOnClickListener(this);
		fb.setOnLongClickListener(this);
	}

	@Override
	public void onActivityEvent(FloatingButton fb, ActivityDelegate a, long e) {
		if ((e & (FRAGMENT_CHANGED | FRAGMENT_CONTENT_CHANGED)) != 0) updateIcon(fb);
	}

	private void updateIcon(FloatingButton fb) {
		MainActivityDelegate a = MainActivityDelegate.get(fb.getContext());
		Action action = Action.get(a.getPrefs().getIntPref(MainActivityPrefs.FAB2_ACTION));
		fb.setImageResource(iconFor(action));
	}

	@DrawableRes
	private int iconFor(@Nullable Action action) {
		if (action == Action.FULLSCREEN_TOGGLE) return R.drawable.fullscreen;
		if (action == Action.VOLUME_MUTE_UNMUTE) return R.drawable.volume_mute;
		if (action == Action.DIM_TOGGLE) return R.drawable.dim_screen;
		return R.drawable.play_pause;
	}

	@Override
	public void onClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		Action action = Action.get(a.getPrefs().getIntPref(MainActivityPrefs.FAB2_ACTION));
		if (action != null) action.getHandler().handle(a.getMediaSessionCallback(), a, uptimeMillis());
	}

	@Override
	public boolean onLongClick(View v) {
		MainActivityDelegate a = MainActivityDelegate.get(v.getContext());
		OverlayMenu menu = a.findViewById(R.id.control_menu);
		menu.show(b -> {
			b.setSelectionHandler(item -> {
				Action action = item.getData();
				if (action != null) {
					action.getHandler().handle(a.getMediaSessionCallback(), a, uptimeMillis());
				}
				return true;
			});
			for (int i = 0; i < OFFERED_ACTIONS.size(); i++) {
				Action action = OFFERED_ACTIONS.get(i);
				b.addItem(UiUtils.getArrayItemId(i), iconFor(action), action.getName()).setData(action);
			}
		});
		return true;
	}
}
