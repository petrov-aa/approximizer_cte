import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.TextAttribute;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.text.AttributedString;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.filechooser.FileFilter;


/**
 * 
 * @author Alexander Petrov (E-mail: petrov.aa@phystech.edu, a.petrov@live.ru)
 *
 */
public class Approximizer extends JPanel implements ActionListener, MouseMotionListener, MouseWheelListener {
	
	private static final long serialVersionUID = -1538096294454334384L;
	
	static String appName = "Approximizer";
	static Dimension drawingSize = new Dimension(600,600);
	
	JFrame window;
	JButton openButton;
	JLabel fileNameLabel;
	
	String settingsFilePath = "Approximizer_settings.ini";
	File defaultFilePath;
	File currentFile;
	double[] t, volume;
	double max_t, min_t, max_volume, min_volume;
	int drawingWidth, drawingHeight;
	int axisWidth = 17;
	int plotWidth, plotHeight, plotXOrig, plotYOrig;
	
	double[] refX = new double[4];
	int overRefN = -1;
	int x_shift = 0;

	public Approximizer(JFrame window, JButton openButton, JLabel fileNameLabel) {
		this.window = window;
		this.openButton = openButton;
		this.fileNameLabel = fileNameLabel;
		
		currentFile = null;
		t = null;
		volume = null;
		
		try(BufferedReader br = new BufferedReader(new FileReader(settingsFilePath))) {
			String line;
			
			defaultFilePath = null;
			while((line= br.readLine())!=null) {
				StringTokenizer st = new StringTokenizer(line,"=");
				String option = st.nextToken();
				if(option.equals("fp")) {
					defaultFilePath = new File(st.nextToken());
				}
			}		
			
		} catch (IOException ee) {
			
		} finally {
			if(defaultFilePath==null) {
				defaultFilePath = new File(".");
			}
		}
	}

	public static void main(String[] args) {
		
		JFrame window = new JFrame(appName);
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension screenSize = toolkit.getScreenSize();
		
		Container contentPane = window.getContentPane();
		contentPane.setLayout(new BorderLayout());
		
		JPanel controls = new JPanel();
		contentPane.add(controls, BorderLayout.NORTH);
		controls.setLayout(new BorderLayout());
		controls.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));
		JLabel fileNameLabel = new JLabel("<no data file opened>");
		controls.add(fileNameLabel, BorderLayout.CENTER);
		JButton openButton = new JButton("Open");
		controls.add(openButton, BorderLayout.EAST);
		
		
		Approximizer app = new Approximizer(window,openButton, fileNameLabel);
		app.setPreferredSize(drawingSize);
		contentPane.add(app, BorderLayout.CENTER);
		
		app.addMouseMotionListener(app);
		app.addMouseWheelListener(app);
		openButton.addActionListener(app);
		
		window.pack();
		Dimension appSize = window.getSize();
		Point windowLocation = new Point(
				screenSize.width/2-appSize.width/2,
				screenSize.height/2-appSize.height/2);
		window.setSize(appSize);
		window.setLocation(windowLocation);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		
		window.setVisible(true);
		window.setMinimumSize(new Dimension(200,200));
	}
	
	private double[] fit(double x_start, double x_finish) {
		
		double xymean = 0;
		double xmean = 0;
		double ymean = 0;
		double x2mean = 0;
		int n = 0;
		
		for(int i=0; i<t.length; i++) {
			if(t[i]>x_start && t[i]<x_finish) {
				xymean += t[i] * volume[i];
				xmean += t[i];
				ymean += volume[i];
				x2mean += t[i]*t[i];
				n++;
			}
		}
		
		double[] ab = new double[2];
		ab[0] = ((double)n*xymean-xmean*ymean)/((double)n*x2mean-xmean*xmean);
		ab[1] = (ymean-ab[0]*xmean)/(double)n;
		
		return ab;
	}
	
	@Override
	public void paintComponent(Graphics g) {
		updateDimensions();
		
		// background
		g.setColor(Color.white);
		g.fillRect(0, 0, drawingWidth, drawingHeight);
		
		// data
		if(currentFile != null) {
			
			//axis
			g.setColor(Color.black);
			g.drawLine(plotXOrig-1, plotYOrig, plotXOrig-1, axisWidth-1);
			g.drawLine(plotXOrig-1, plotYOrig, drawingWidth-axisWidth, plotYOrig);
			g.drawLine(drawingWidth-axisWidth, plotYOrig, drawingWidth-axisWidth, axisWidth-1);
			g.drawLine(plotXOrig-1, axisWidth-1, drawingWidth-axisWidth, axisWidth-1);
			
			g.setColor(Color.red);
			for(int i=0, j=1; j<t.length; j++, i++) {
				g.drawLine(x(t[i]), y(volume[i]), x(t[j]), y(volume[j]));
			}
			
			//fit lines
			double[] ab1 = fit(refX[0],refX[1]);
			double[] ab2 = fit(refX[2],refX[3]);
			g.setColor(Color.BLUE);
			g.drawLine(x(min_t), y(min_t*ab1[0]+ab1[1]), x(max_t), y(max_t*ab1[0]+ab1[1]));
			g.drawLine(x(min_t), y(min_t*ab2[0]+ab2[1]), x(max_t), y(max_t*ab2[0]+ab2[1]));
			
			//Tg
			double Tg = (ab2[1]-ab1[1])/(ab1[0]-ab2[0]);
			double V = (ab1[0]*Tg+ab1[1]);
			g.fillOval(x(Tg)-8, y(V)-8, 15, 15);
			AttributedString Tg_str = new AttributedString(String.format(Locale.ENGLISH,"Tg = %.4f",Tg));
			Tg_str.addAttribute(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB, 1,2);
			g.drawString(Tg_str.getIterator(), x(Tg)+5, y(V)+20);
			
			//reference lines
			g.setColor(Color.cyan);
			g.drawLine(x(refX[0]), 0, x(refX[0]), drawingHeight);
			g.drawLine(x(refX[1]), 0, x(refX[1]), drawingHeight);
			g.setColor(Color.magenta);
			g.drawLine(x(refX[2]), 0, x(refX[2]), drawingHeight);
			g.drawLine(x(refX[3]), 0, x(refX[3]), drawingHeight);
			g.setColor(Color.red);
			g.fillRect(x(refX[0])-10, 3, 20, 12);
			g.fillRect(x(refX[1])-10, 18, 20, 12);
			g.fillRect(x(refX[2])-10, 33, 20, 12);
			g.fillRect(x(refX[3])-10, 48, 20, 12);
			g.setColor(Color.white);
			Font defaultFont = g.getFont();
			g.setFont(new Font(g.getFont().getFontName(),Font.BOLD,12));
			g.drawString("1", x(refX[0])-3, 14);
			g.drawString("2", x(refX[1])-3, 29);
			g.drawString("3", x(refX[2])-3, 44);
			g.drawString("4", x(refX[3])-3, 59);
			g.setFont(defaultFont);
			g.setColor(Color.black);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[0]), x(refX[0])+12, 14);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[1]), x(refX[1])+12, 29);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[2]), x(refX[2])+12, 44);
			g.drawString(String.format(Locale.ENGLISH,"%.4f", refX[3]), x(refX[3])+12, 59);
			
		}
		
	}
	
	public void updateDimensions() {
		Dimension tmp = getSize();
		drawingWidth = tmp.width;
		drawingHeight = tmp.height;
		plotXOrig = axisWidth;
		plotWidth = drawingWidth - axisWidth*2;
		plotHeight = drawingHeight - axisWidth*2;
		plotYOrig = plotHeight + axisWidth;
	}
	
	private int x(double x) {
		return plotXOrig + (int)(plotWidth*(x-min_t)/(max_t-min_t));
	}
	
	private int y(double y) {
		return plotYOrig - (int)(plotHeight*(y-min_volume)/(max_volume-min_volume));
	}
	
	private double xx(int x) {
		return (double)(x-axisWidth)*(max_t-min_t)/(double)plotWidth + min_t;
	}
	
	public void loadFile(File dataFile) {
		
		boolean errorOccured = false;
		
		int lines = 0;
		
		try(LineNumberReader lnr = new LineNumberReader(new FileReader(dataFile))) {
			lnr.skip(Long.MAX_VALUE);
			lines = lnr.getLineNumber();
		} catch (IOException e) {
			errorOccured = true;
		}
		
		if(!errorOccured)
		try(BufferedReader br = new BufferedReader(new FileReader(dataFile))) {
						
			
			double[] t = new double[lines];
			double[] volume = new double[lines];
			
			max_t = max_volume = Double.MIN_VALUE;
			min_t = min_volume = Double.MAX_VALUE;
			
			for(int i=0; i<lines; i++) {
				StringTokenizer st = new StringTokenizer(br.readLine());
				t[i] = Double.parseDouble(st.nextToken());
				volume[i] = Double.parseDouble(st.nextToken());
				if(t[i]>max_t)
					max_t = t[i];
				if(t[i]<min_t)
					min_t = t[i];
				if(volume[i]>max_volume)
					max_volume = volume[i];
				if(volume[i]<min_volume)
					min_volume = volume[i];
				
				refX[0] = (min_t+max_t)*0.1;
				refX[1] = (min_t+max_t)*0.4;
				refX[2] = (min_t+max_t)*0.6;
				refX[3] = (min_t+max_t)*0.9;
			}
			
			
			
			this.t = t;
			this.volume = volume;
			
		} catch (IOException e) {
			errorOccured = true;
		} finally {
			if(!errorOccured) {
				currentFile = dataFile;
			} else {
				currentFile = null;
				t = null;
				volume = null;
			}
			repaint();
		}
		
		if(currentFile != null) {	
			fileNameLabel.setText(currentFile.getName());
			window.setTitle(currentFile.getName()+" � "+appName);
			try{
				fileNameLabel.setToolTipText(currentFile.getCanonicalPath());
			} catch (IOException e) {
				
			}
		}
		
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		if((JButton)e.getSource() == openButton) {
			JFileChooser chooser = new JFileChooser();
			chooser.setAcceptAllFileFilterUsed(false);
			chooser.addChoosableFileFilter(new FileFilter(){
				@Override
				public boolean accept(File f) {
					if(f.isDirectory())
						return true;
					String ext = null;
					String fileName = f.getName();
					int i = fileName.lastIndexOf(".");
					if (i > 0 &&  i < fileName.length() - 1) {
			            ext = fileName.substring(i+1).toLowerCase();
			        }
					if(ext!=null && (ext.equals("dat") || ext.equals("txt"))) {
						return true;
					} else
						return false;
				}
				@Override
				public String getDescription() {
					return "*.dat, *.txt";
				}
			});
			chooser.setCurrentDirectory(defaultFilePath);
			chooser.showDialog(window, "Choose data file");
			File tmp = chooser.getSelectedFile();
			if(tmp != null) {
				PrintStream ps = null;
				try {
					defaultFilePath = tmp.getParentFile().getCanonicalFile();
					ps = new PrintStream(new FileOutputStream(settingsFilePath));
					ps.println("fp="+defaultFilePath.getPath());
				} catch (IOException ee) {
					
				} finally {
					if(ps!=null)
						ps.close();
				}
				loadFile(tmp);
			}
		}
	}
	
	@Override
	public void mouseWheelMoved(MouseWheelEvent e) {
		if(overRefN!=-1) {
			int tmp = x(refX[overRefN]) - e.getUnitsToScroll()/e.getScrollAmount();
			double xx_ = xx(tmp);
			refX[overRefN] = xx_ > max_t ? max_t : (xx_< min_t ? min_t : xx_);
		}
		repaint();
	}

	@Override
	public void mouseDragged(MouseEvent e) {
		if(overRefN!=-1) {
			int x_ = e.getX() - x_shift;
			double xx_ = xx(x_);
			refX[overRefN] = xx_ > max_t ? max_t : (xx_< min_t ? min_t : xx_);
			repaint();
		}
	}

	@Override
	public void mouseMoved(MouseEvent e) {
		if(currentFile!=null) {
			int x_ = e.getX();
			if(Math.abs(x_shift = x_-x(refX[0]))<5) {
				setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
				overRefN = 0;
			} else if(Math.abs(x_shift = x_-x(refX[1]))<5) {
				setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
				overRefN = 1;
			} else if(Math.abs(x_shift = x_-x(refX[2]))<5) {
				setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
				overRefN = 2;
			} else if(Math.abs(x_shift = x_-x(refX[3]))<5) {
				setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
				overRefN = 3;
			} else {
				setCursor(Cursor.getDefaultCursor());
				overRefN = -1;
			}
		}
	}

}