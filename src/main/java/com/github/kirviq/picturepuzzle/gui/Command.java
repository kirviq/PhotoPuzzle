package com.github.kirviq.picturepuzzle.gui;

@FunctionalInterface
public interface Command {
	void trigger(Gui gui);
}
