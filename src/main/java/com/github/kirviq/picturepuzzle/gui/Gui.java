package com.github.kirviq.picturepuzzle.gui;

import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;

@Slf4j
public class Gui {
	private static final int START_WIDTH = 400;
	private static final int START_HEIGHT = 600;

	private final ImagePanel imagePanel;
	private final JFrame frame;
	private double aspectRate = (double) START_WIDTH / START_HEIGHT;
	//	private final JLabel messageBar;

	@Builder
	public Gui(Command showNext, Command changeDir, Command showHelp) {
		JPanel root = new JPanel();
		OverlayLayout layout = new OverlayLayout(root);
		root.setLayout(layout);
		this.imagePanel = new ImagePanel(this::setAspectRate);

		{ // panel with buttons
			Box panel = new Box(BoxLayout.X_AXIS);
			panel.setOpaque(false);

			{ // actions
				addButton(panel, () -> changeDir.trigger(this), "/drawable/gears.png");
				addButton(panel, imagePanel::undo, "/drawable/undo.png");
				addButton(panel, this::triggerHelp, "/drawable/questionmark.png");
				addButton(panel, () -> showHelp.trigger(this), "/drawable/info.png");
				panel.add(Box.createHorizontalGlue());
				addButton(panel, () -> showNext.trigger(this), "/drawable/nextbutton.png");
			}
			panel.setAlignmentY(0f);
			panel.setAlignmentX(.5f);
			root.add(panel);
			root.setMinimumSize(max(root.getMinimumSize(), panel.getPreferredSize()));
		}
		{ // image area
			imagePanel.setAlignmentX(0.5f);
			imagePanel.setAlignmentY(0f);
			root.add(imagePanel);
			root.setMinimumSize(max(root.getMinimumSize(), new Dimension(100, 100)));
		}
		{ // put everything in a frame
			frame = new JFrame("Photo Puzzle");
			frame.addComponentListener(new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					Rectangle newArea = e.getComponent().getBounds();
					int pixels = newArea.height * newArea.width;
					int newHeight = (int) Math.sqrt(pixels / aspectRate);
					int newWidth = pixels / newHeight;
					e.getComponent().setBounds(newArea.x, newArea.y, newWidth, newHeight);
				}
			});
			frame.setContentPane(root);
			frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
			frame.setSize(START_WIDTH, START_HEIGHT);
			frame.setMinimumSize(root.getMinimumSize());
			frame.setVisible(true);
		}
	}
	
	private void triggerHelp() {
		imagePanel.setShowHelp(true);
		imagePanel.repaint();
	}
	
	private void setAspectRate(double rate) {
		this.aspectRate = rate;
		Dimension size = frame.getSize();
		int pixels = size.height * size.width;
		size.height = (int) Math.sqrt(pixels / rate);
		size.width = pixels / size.height;
		frame.setSize(size);
	}

	private Dimension max(Dimension s1, Dimension s2) {
		return new Dimension(Math.max(s1.width, s2.width), Math.max(s1.height, s2.height));
	}

	@SneakyThrows
	private void addButton(Container panel, Runnable action, String image) {
		try (InputStream in = getClass().getResourceAsStream(image)) {
			BufferedImage icon = ImageIO.read(in);

			((Graphics2D) icon.getGraphics()).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .77f));
			JButton button = new JButton(new ImageIcon(icon));
			button.setBorder(BorderFactory.createEmptyBorder());
			button.setContentAreaFilled(false);
			button.setAlignmentX(Component.RIGHT_ALIGNMENT);
			if (action != null) {
				button.addActionListener(e -> action.run());
			}
			panel.add(button);
		}
	}

	public void setImage(BufferedImage image, boolean play) {
		imagePanel.setImage(image, play);
	}
	
	public int getUndoStepCount() {
		return imagePanel.getStepCount();
	}
}
