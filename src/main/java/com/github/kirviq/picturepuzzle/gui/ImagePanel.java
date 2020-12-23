package com.github.kirviq.picturepuzzle.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("NonSerializableFieldInSerializableClass") // Why The Fuck are JPanels serializable?
@Slf4j
class ImagePanel extends JPanel {
	private int cols = 5;
	private int rows = 5;
	private final DoubleConsumer aspectRateListener;

	private BufferedImage image;
	private final BufferedImage transparent;
	private List<Field> fields;
	private BufferedImage scaledImage;
	private boolean enableGame = false;
	@Setter
	private boolean showHelp = false;
	private final Deque<Move> history = new LinkedList<>();
	
	@AllArgsConstructor
	@RequiredArgsConstructor
	@EqualsAndHashCode(of = {"x", "y"})
	private class Field {
		int i;
		final int x;
		final int y;

		int getNum() {
			return x + y * cols;
		}
		
		boolean isLast() {
			return (x == cols - 1) && (y == rows - 1);
		}
	}

	@Value
	private static class Move {
		int field1;
		int field2;
	}

	private void render(Graphics g, Field source, Field target) {
		double w = (double) getWidth() / cols;
		double h = (double) getHeight() / rows;
		double x = w * source.x;
		double y = h * source.y;
		if (source.isLast()) {
			g.drawImage(transparent,
					(int) (target.x * w), (int) (target.y * h),
					(int) ((target.x + 1) * w), (int) ((target.y + 1) * h),
					0, 0, transparent.getWidth(), transparent.getHeight(), null);
		} else {
			g.drawImage(scaledImage, (int) (target.x * w), (int) (target.y * h), (int) ((target.x + 1) * w), (int) ((target.y + 1) * h), (int) x, (int) y, (int) (x + w), (int) (y + h), null);
			if (showHelp) {
				g.setFont(new Font("Arial", Font.BOLD, 28));
				g.setColor(Color.BLACK);
				String text = String.valueOf(source.i + 1);
				g.drawString(text, (int) (target.x * w + 10), (int) (target.y * h + 26));
				g.setColor(Color.ORANGE);
				g.drawString(text, (int) (target.x * w + 10), (int) (target.y * h + 80));
			}
		}
	}

	private int num(int left, int top) {
		return left + top * cols;
	}

	private Set<Field> getDirectNeighbors() {
		int empty = findEmptyField();
		int row = empty / cols;
		int col = empty % cols;
		Set<Field> available = new HashSet<>();
		if (col > 0) {
			available.add(new Field(col - 1, row));
		}
		if (row > 0) {
			available.add(new Field(col, row - 1));
		}
		if (row < rows - 1) {
			available.add(new Field(col, row + 1));
		}
		if (col < cols - 1) {
			available.add(new Field(col + 1, row));
		}
		return available;
	}

	@SneakyThrows
	public ImagePanel(DoubleConsumer aspectRateListener) {
		try (InputStream in = getClass().getResourceAsStream("/drawable/transparent.png")) {
			this.transparent = ImageIO.read(in);
		}
		this.aspectRateListener = aspectRateListener;
		addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				scaleToFit();
				repaint();
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				if (!enableGame) {
					return;
				}
				int clickedColumn = (e.getX() * cols + 1)/ getWidth();
				int clickedRow = (e.getY() * rows + 1)/ getHeight();
				Field click = new Field(clickedColumn, clickedRow);
				int empty = findEmptyField();
				int emptyRow = empty / cols;
				int emptyColumn = empty % cols;
				if (clickedColumn == emptyColumn) {
					for (int current = emptyRow; current < clickedRow; current++) {
						flip(new Field(clickedColumn, current + 1).getNum(), new Field(clickedColumn, current).getNum());
					}
					for (int current = emptyRow; current > clickedRow; current--) {
						flip(new Field(clickedColumn, current - 1).getNum(), new Field(clickedColumn, current).getNum());
					}
				}
				if (clickedRow == emptyRow) {
					for (int current = emptyColumn; current < clickedColumn; current++) {
						flip(new Field(current + 1, clickedRow).getNum(), new Field(current, clickedRow).getNum());
					}
					for (int current = emptyColumn; current > clickedColumn; current--) {
						flip(new Field(current - 1, clickedRow).getNum(), new Field(current, clickedRow).getNum());
					}
				}
				if (isSolved()) {
					enableGame = false;
				}
				repaint();
			}
		});
	}

	public boolean isSolved() {
		return IntStream.range(0, fields.size() - 1)
				.allMatch(i -> fields.get(i).i == i);
	}

	public int getStepCount() {
		return history.size();
	}
	public void undo() {
		if (history.isEmpty()) {
			return;
		}
		Move lastMove = history.pop();
		Field o = fields.get(lastMove.field1);
		fields.set(lastMove.field1, fields.get(lastMove.field2));
		fields.set(lastMove.field2, o);
		repaint();
	}

	private void flip(int i, int j) {
		history.push(new Move(i, j));
		Field o = fields.get(i);
		fields.set(i, fields.get(j));
		fields.set(j, o);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (image == null) {
			return;
		}
		if (enableGame) {
			int myWidth = getWidth();
			int myHeight = getHeight();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, myWidth, myHeight);

			for (int col = 0; col < cols; col++) {
				for (int row = 0; row < rows; row++) {
					Field source = fields.get(col + row * cols);
					render(g, source, new Field(col, row));
				}
			}
			g.setColor(Color.BLACK);
			for (int col = 0; col < cols; col++) {
				int x = col * myWidth / cols;
				g.drawLine(x - 1, 0, x - 1, myHeight);
				g.drawLine(x, 0, x, myHeight);
				g.drawLine(x + 1, 0, x + 1, myHeight);
			}
			for (int row = 0; row < rows; row++) {
				int y = row * myHeight / rows;
				g.drawLine(0, y - 1, myWidth, y - 1);
				g.drawLine(0, y, myWidth, y);
				g.drawLine(0, y + 1, myWidth, y + 1);
			}
		} else {
			g.drawImage(scaledImage, 0, 0, null);
		}
	}

	@SneakyThrows
	void setImage(BufferedImage image, boolean play) {
		this.showHelp = false;
		history.clear();
		if (SwingUtilities.isEventDispatchThread()) {
			setImageInternal(image, play);
		} else {
			SwingUtilities.invokeAndWait(() -> {
				setImageInternal(image, play);
			});
		}
	}

	private void setImageInternal(BufferedImage image, boolean play) {
		this.image = image;
		determineLayout();
		scaleToFit();
		
		if (play) {
			fields = new ArrayList<>();
			int count = 0;
			for (int row = 0; row < rows; row++) {
				for (int col = 0; col < cols; col++) {
					fields.add(new Field(count++, col, row));
				}
			}
			for (int i = 0; i < 10000; i++) {
				int empty = findEmptyField();
				Set<Field> allowed = getDirectNeighbors();
				Field move = new ArrayList<>(allowed).get((int) (allowed.size() * Math.random()));
				flip(empty, move.getNum());
			}
			enableGame = true;
		}
		repaint();
	}
	
	private int findEmptyField() {
		return IntStream.range(0, fields.size())
							.filter(j -> fields.get(j).isLast())
							.findFirst().orElse(-1);
	}
	
	private void determineLayout() {
		double rate = (double) image.getWidth() / image.getHeight();
		if (rate > 1.25) {
			cols = 6;
			rows = 4;
			aspectRateListener.accept(1.5);
		} else if (rate > 0.8333) {
			cols = 5;
			rows = 5;
			aspectRateListener.accept(1);
		} else {
			cols = 4;
			rows = 6;
			aspectRateListener.accept(4d/6);
		}
	}

	private void scaleToFit() {
		if (image == null) {
			return;
		}
		int sw = image.getWidth();
		int sh = image.getHeight();
		log.debug("image is {} x {}", sw, sh);


		int x, y;
		double scale;
		// landscape image
		int dh = getHeight(), dw = getWidth();
		log.debug("screen size: {} x {}", dw, dh);
		double sRate = (double) sw / sh;
		double dRate = (double) dw / dh;
		if (sRate < dRate) {
			// too high, cut top and bottom
			log.debug("too high");
			scale = (double) dw / sw;
			x = 0;
			y = (int) ((dh / scale - sh) / 2);
		} else {
			// too wide, cut left and right
			log.debug("too wide");
			scale = (double) dh / sh;
			x = (int) (dw / scale - sw) / 2;
			y = 0;
		}
		log.debug("scale by {}", scale);
		log.debug("pan to {} x {}", x, y);
		AffineTransform transformation = new AffineTransform();
		transformation.setToScale(scale, scale);
		transformation.translate(x, y);

		BufferedImage result = new BufferedImage(dw, dh, image.getType());
		Graphics2D graphics = (Graphics2D) result.getGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setColor(Color.CYAN);
		graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
		graphics.drawImage(image, transformation, null);
		this.scaledImage = result;
	}
}