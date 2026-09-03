package me.aap.fermata.ui.view;

import static me.aap.utils.ui.fragment.ViewFragmentMediator.attachMediator;

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
		// Always resolve to the same singleton mediator regardless of the active fragment, but
		// still go through attachMediator(...) so it actually calls enable()/disable() on it --
		// calling setMediator(Mediator) directly would only set the field, never invoke enable(),
		// leaving the button's icon unset and its click/long-click listeners never attached.
		// Widened to the FloatingButton type explicitly (as the first positional argument only):
		// SecondaryFabMediator implements ViewFragmentMediator<FloatingButton> (via
		// FloatingButton.Mediator), and Java generics are invariant, so inferring V as
		// SecondaryFloatingButton here would fail to typecheck. getMediator/setMediator are still
		// referenced via `this` (not `fb`) since accessing a protected inherited member through a
		// reference declared as the superclass type is illegal from a different package.
		FloatingButton fb = this;
		return attachMediator(fb, f, () -> SecondaryFabMediator.instance, this::getMediator,
				this::setMediator);
	}
}
