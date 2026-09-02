package me.aap.fermata.ui.view;

import android.content.Context;
import android.util.AttributeSet;

import me.aap.fermata.ui.fragment.SecondaryFabMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * A second, user-configurable FAB shown alongside the primary one during video playback. Its
 * behavior is constant (not fragment-dependent), so it always attaches one static mediator
 * instead of participating in the primary FAB's per-fragment mediator switching.
 */
public class SecondaryFloatingButton extends FloatingButton {

	public SecondaryFloatingButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected boolean setMediator(ActivityFragment f) {
		setMediator(SecondaryFabMediator.instance);
		return true;
	}
}
