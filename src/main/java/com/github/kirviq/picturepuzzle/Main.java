package com.github.kirviq.picturepuzzle;

import com.github.kirviq.picturepuzzle.gui.DirectoryPicker;
import com.github.kirviq.picturepuzzle.gui.Gui;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class Main {
	private static final int PREREAD_BUFFER_SIZE = 1;
	private static File baseDir;
	private static BlockingQueue<Image> files;

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
					File selectedDir = selectInputDir();
					if (selectedDir != null) {
						Main.baseDir = selectedDir;
						g.setImage(imageWithText("scanning..."), false);
						files = getFiles(Main.baseDir);
						showNext(files, g);
					}
				})
				.build();
		gui.setImage(imageWithText("scanning..."), false);
		showNext(files, gui);
	}

	private static void showNext(BlockingQueue<Image> files, Gui g) {
		try {
			Image take = files.take();
			g.setImage(take.image, take.game);
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

	@Value
	private static class Image {
		private BufferedImage image;
		private boolean game;
	}
	private static BlockingQueue<Image> getFiles(File baseDir) {
		List<File> files;
		try (Stream<Path> stream = Files.walk((baseDir).toPath(), FileVisitOption.FOLLOW_LINKS)) {
			files = stream
					.map(Path::toFile)
					.filter(File::isFile)
					.filter(f -> f.getName().matches("(?i).*\\.(jpe?g|png)"))
					.collect(Collectors.toList());
			Collections.shuffle(files);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		BlockingQueue<Image> images = new ArrayBlockingQueue<>(PREREAD_BUFFER_SIZE);
		new Thread(() -> {
			files.stream()
					.map(Main::readImage)
					.filter(Objects::nonNull)
					.forEach((e) -> {
						try {
							images.put(new Image(e, true));
						} catch (InterruptedException e1) {
							log.warn("interrupted");
						}
					});
			try {
				//noinspection InfiniteLoopStatement
				while (true) {
					// yes, it's not good, but aside from one dead stream per directory reconfiguration it should be ok
					images.put(new Image(imageWithText("no more images"), false));
				}
			} catch (InterruptedException e) {
				// jahaa
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
		BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
		Graphics graphics = image.getGraphics();
		graphics.setColor(Color.LIGHT_GRAY);
		graphics.fillRect(0, 0, 500, 500);
		graphics.setColor(Color.BLACK);
		graphics.setFont(new Font("Arial Black", Font.BOLD, 50));
		graphics.drawString(text, 50, 200);
		return image;
	}
}
