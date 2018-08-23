package com.github.kirviq.picturepuzzle;

import com.github.kirviq.picturepuzzle.gui.DirectoryPicker;
import com.github.kirviq.picturepuzzle.gui.Gui;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

@Slf4j
public class Main {
	private static final int PREREAD_BUFFER_SIZE = 1;
	private static File baseDir;
	private static BlockingQueue<BufferedImage> files;

	public static void main(String[] args) {
		System.setProperty("awt.useSystemAAFontSettings","on");
		System.setProperty("swing.aatext", "true");
		String basePath = Preferences.userNodeForPackage(Main.class).get("basedir", null);
		baseDir = basePath == null ? selectInputDir() : new File(basePath);
		if (baseDir == null) {
			log.info("need directory with images");
			System.exit(1);
		}
		files = getFiles(baseDir);
		Gui gui = Gui.builder()
				.showNext(g -> showNext(files, g))
				.changeDir(g -> {
					baseDir = selectInputDir();
					files = getFiles(baseDir);
					showNext(files, g);
				})
				.build();
		showNext(files, gui);
	}

	private static void showNext(BlockingQueue<BufferedImage> files, Gui g) {
		try {
			BufferedImage take = files.take();
			if (take != null) {
				g.setImage(take, true);
			} else {
				g.setImage(imageWithText("no more images"), true);
			}
		} catch (InterruptedException e) {
			log.error("interrupted", e);
		}
	}

	private static File selectInputDir() {
		File directory = DirectoryPicker.builder().title("Select Input Directory").build().pickFile();
		if (directory != null) {
			Preferences.userNodeForPackage(Main.class).put("basedir", directory.getAbsolutePath());
		}
		return directory;
	}

	private static BlockingQueue<BufferedImage> getFiles(File baseDir) {
		List<File> files;
		try {
			files = Files.walk((baseDir).toPath())
					.map(Path::toFile)
					.filter(File::isFile)
					.filter(f -> f.getName().matches("(?i).*\\.(jpe?g|png)"))
					.collect(Collectors.toList());
			Collections.shuffle(files);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		BlockingQueue<BufferedImage> images = new ArrayBlockingQueue<>(PREREAD_BUFFER_SIZE);
		new Thread(() -> {
			files.stream()
					.map(Main::readImage)
					.filter(Objects::nonNull)
					.forEach((e) -> {
						try {
							images.put(e);
						} catch (InterruptedException e1) {
							log.warn("interrupted");
						}
					});
			while (true) {
				images.add(null);
			}
		}).start();
		return images;
	}

	private static BufferedImage readImage(File file) {
		try {
			return ImageIO.read(file);
		} catch (IOException e) {
			log.error("trouble reading image", e);
			return null;
		}
	}

	private static BufferedImage imageWithText(String text) {
		BufferedImage image = new BufferedImage(170, 30, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = image.getGraphics();
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.fillRect(0, 0, 200, 50);
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font("Arial Black", Font.BOLD, 20));
		graphics.drawString(text, 10, 25);
		return image;
	}
}
