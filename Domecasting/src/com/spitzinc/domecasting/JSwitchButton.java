package com.spitzinc.domecasting;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.AbstractButton;
import javax.swing.DefaultButtonModel;
import javax.swing.Timer;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

/**
 * Cool little animated switch to turn on/off some option. We're using this in Domecast host pane
 * to listen (or not listen) to the domecast that is in progress.
 * 
 * @author cweisbrod
 *
 */
public class JSwitchButton extends AbstractButton
{
	private static final long serialVersionUID = 1L;
	private static final double kButtonAnimationInterval = 0.125;

	private Color buttonBackground;
	private Color buttonForeground;
	private Color buttonForegroundDisabled;
	private Font buttonFont;
	private Color selectedBackgroundColor = new Color(93, 182, 223, 255);
	private Color unselectedBackgroundColor  = new Color(160, 160, 160, 255);
	private Color black  = new Color(0,0,0,255);
		
	private Color notchColor1 = new Color(220,220,220);
	private Color notchColor2 = new Color(120,120,120);
	private Color notchColor3 = new Color(150,150,150);
	private Color notchColor4 = new Color(170,170,170);
	
	private int gap;
	private int globalWitdh;
	private final String trueLabel;
	private final String falseLabel;
	private Dimension thumbBounds;
	private int max;

	// Button animation
	private Timer buttonAnimationTimer;
	private int buttonXStart;
	private int buttonXEnd;
	private long animationStart;

	public JSwitchButton(String trueLabel, String falseLabel)
	{
		UIDefaults defaults = UIManager.getDefaults();
		this.buttonBackground = defaults.getColor("Button.background");
		this.buttonForeground = defaults.getColor("Button.foreground");
		this.buttonForegroundDisabled = defaults.getColor("Button.disabledForeground");
		this.buttonFont = defaults.getFont("Button.font");
		
		this.buttonAnimationTimer = new Timer(20, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                repaint();
            }
        });
		
		this.trueLabel = trueLabel;
		this.falseLabel = falseLabel;
		double trueLength = getFontMetrics(buttonFont).getStringBounds(trueLabel, getGraphics()).getWidth();
		double falseLength = getFontMetrics(buttonFont).getStringBounds(falseLabel, getGraphics()).getWidth();
		max = (int)Math.max(trueLength, falseLength);
		gap =  Math.max(5, 5 + (int)Math.abs(trueLength - falseLength)); 
		thumbBounds  = new Dimension(max + gap * 2, 20);
		globalWitdh =  2 * thumbBounds.width;
		setModel( new DefaultButtonModel() );
		setSelected( false );
		addMouseListener( new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (isEnabled() && new Rectangle( getPreferredSize() ).contains( e.getPoint() ))
				{
					boolean wasSelected = isSelected();
					setSelected(!wasSelected);
					
					// Notify each ActionListener
					fireActionPerformed(new ActionEvent(JSwitchButton.this, ActionEvent.ACTION_PERFORMED, null));
					
					// Setup button animation boundaries and start the animation
					if (wasSelected)
					{
						buttonXStart = thumbBounds.width;
						buttonXEnd = 0;
					}
					else
					{
						buttonXStart = 0;
						buttonXEnd = thumbBounds.width;
					}
					animationStart = System.currentTimeMillis();
					buttonAnimationTimer.start();
				}
			}
		});
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(globalWitdh, thumbBounds.height);
	}

	@Override
	public int getHeight() {
		return getPreferredSize().height;
	}

	@Override
	public int getWidth() {
		return getPreferredSize().width;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		final int kArcSize = 4;
		
		Graphics2D g2 = (Graphics2D)g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		int componentWidth = getWidth();
		int componentHeight = getHeight();
		int halfComponentWidth = componentWidth / 2;
		int halfArcSize = kArcSize / 2;

		// Draw the colored background of the entire component
		g.setColor(selectedBackgroundColor);
		g.fillRoundRect(1, 1, kArcSize, componentHeight - 2, kArcSize, kArcSize);
		g.fillRoundRect(halfArcSize, 1, halfComponentWidth - halfArcSize, componentHeight - 2, 0, 0);
		g.setColor(unselectedBackgroundColor);
		g.fillRoundRect(halfComponentWidth, 1, halfComponentWidth - halfArcSize, componentHeight - 2, 0, 0);
		g.fillRoundRect(componentWidth - kArcSize - 1, 1, kArcSize, componentHeight - 2, kArcSize, kArcSize);
		
		// Draw border around component
		if (isEnabled())
			g2.setColor(black);
		else
			g2.setColor(buttonForegroundDisabled);
		g2.drawRoundRect(1, 1, componentWidth - 2 - 1, componentHeight - 2 - 1, kArcSize, kArcSize);
		
		// Look at current time to determine where button should be
		int x;
		double progress = (double)(System.currentTimeMillis() - animationStart) / 1000.0 / kButtonAnimationInterval;
		if (progress > 1.0)
		{
			buttonAnimationTimer.stop();
			x = buttonXEnd;
		}
		else
			x = (int)((double)buttonXStart + (double)(buttonXEnd - buttonXStart) * progress);

		int y = 0;
		int w = thumbBounds.width;
		int h = thumbBounds.height;
		
		// Draw text
		if (isEnabled())
			g2.setColor(buttonForeground);
		else
			g2.setColor(buttonForegroundDisabled);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2.setFont(buttonFont);
		FontMetrics fm = g2.getFontMetrics(buttonFont);
		Rectangle2D textBounds = fm.getStringBounds(trueLabel, g);
		int stringX = 1 + (int)((w - textBounds.getWidth()) / 2.0);
		int stringY = (int)(((h - textBounds.getHeight()) / 2.0) + textBounds.getHeight()) - 1;
		g2.drawString(trueLabel, stringX, stringY);
		textBounds = fm.getStringBounds(falseLabel, g);
		stringX = w + (int)((w - textBounds.getWidth()) / 2.0);
		stringY = (int)(((h - textBounds.getHeight()) / 2.0) + textBounds.getHeight()) - 1;
		g2.drawString(falseLabel, stringX, stringY);
		
		// Draw button
		g2.setPaint(new GradientPaint((float)x, (float)y, buttonBackground.darker(), (float)x, (float)(y + h), buttonBackground.brighter()));
		g2.fillRect(x, y, w, h);

		// Draw notches on button
		if (w > 14)
		{
			final int size = 10;
			g2.setColor(notchColor1);
			g2.fillRect(x+w/2-size/2,y+h/2-size/2, size, size);
			g2.setColor(notchColor2);
			g2.fillRect(x+w/2-4,h/2-4, 2, 2);
			g2.fillRect(x+w/2-1,h/2-4, 2, 2);
			g2.fillRect(x+w/2+2,h/2-4, 2, 2);
			g2.setColor(notchColor3);
			g2.fillRect(x+w/2-4,h/2-2, 2, 6);
			g2.fillRect(x+w/2-1,h/2-2, 2, 6);
			g2.fillRect(x+w/2+2,h/2-2, 2, 6);
			g2.setColor(notchColor4);
			g2.fillRect(x+w/2-4,h/2+2, 2, 2);
			g2.fillRect(x+w/2-1,h/2+2, 2, 2);
			g2.fillRect(x+w/2+2,h/2+2, 2, 2);
		}

		// Draw border around button
		if (isEnabled())
			g2.setColor(black);
		else
			g2.setColor(buttonForegroundDisabled);
		g2.drawRoundRect(x, y, w - 1, h - 1, kArcSize, kArcSize);
	}
}
