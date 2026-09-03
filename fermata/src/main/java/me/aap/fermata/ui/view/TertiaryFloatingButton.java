package me.aap.fermata.ui.view;

import static me.aap.utils.ui.fragment.ViewFragmentMediator.attachMediator;

import android.content.Context;
import android.util.AttributeSet;

import me.aap.fermata.ui.fragment.TertiaryFabMediator;
import me.aap.utils.ui.fragment.ActivityFragment;
import me.aap.utils.ui.view.FloatingButton;

/**
 * A third, optional, user-configurable FAB. See {@link SecondaryFloatingButton} for why it
 * always attaches one static mediator instead of participating in the primary FAB's
 * per-fragment mediator switching.
 */
public class TertiaryFloatingButton extends FloatingButton {

	public TertiaryFloatingButton(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected boolean setMediator(ActivityFragment f) {
		FloatingButton fb = this;
		return attachMediator(fb, f, () -> TertiaryFabMediator.instance, this::getMediator,
				this::setMediator);
	}
}
