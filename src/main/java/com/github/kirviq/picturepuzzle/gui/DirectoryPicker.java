package com.github.kirviq.picturepuzzle.gui;

import lombok.Builder;

import javax.swing.JFileChooser;
import javax.swing.JPanel;
import java.io.File;

@Builder
public class DirectoryPicker {
	private final String title;
//	private final File startDir;

	public File pickFile() {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		fc.setAcceptAllFileFilterUsed(false);
		fc.setDialogTitle(title == null ? "pick a directory" : title);
//		fc.setCurrentDirectory(startDir);

		if (fc.showOpenDialog(new JPanel()) == JFileChooser.APPROVE_OPTION) {
			return fc.getSelectedFile();
		} else {
			return null;
		}
	}
}
